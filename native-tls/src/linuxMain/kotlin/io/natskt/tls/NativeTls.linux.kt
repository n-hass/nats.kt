@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.natskt.tls

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.Selectable
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.tls.TlsException
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.natskt.tls.internal.LinuxSslEngine
import io.natskt.tls.internal.parseDerCert
import io.natskt.tls.openssl.SSL
import io.natskt.tls.openssl.SSL_CTX
import io.natskt.tls.openssl.SSL_CTX_ctrl
import io.natskt.tls.openssl.SSL_CTX_free
import io.natskt.tls.openssl.SSL_CTX_new
import io.natskt.tls.openssl.SSL_CTX_set_cert_store
import io.natskt.tls.openssl.SSL_CTX_set_default_verify_paths
import io.natskt.tls.openssl.SSL_ctrl
import io.natskt.tls.openssl.SSL_free
import io.natskt.tls.openssl.SSL_new
import io.natskt.tls.openssl.SSL_set1_host
import io.natskt.tls.openssl.SSL_set_fd
import io.natskt.tls.openssl.SSL_set_verify
import io.natskt.tls.openssl.TLS_client_method
import io.natskt.tls.openssl.X509_STORE_add_cert
import io.natskt.tls.openssl.X509_STORE_new
import io.natskt.tls.openssl.X509_free
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger("LinuxNativeTls")

// OpenSSL <ssl.h> constants (stable across versions).
private const val TLS1_2_VERSION: Long = 0x0303
private const val SSL_VERIFY_NONE: Int = 0x00
private const val SSL_VERIFY_PEER: Int = 0x01

// SSL_CTRL command codes (see <openssl/ssl.h>):
//   #define SSL_CTRL_SET_TLSEXT_HOSTNAME      55
//   #define TLSEXT_NAMETYPE_host_name          0
//   #define SSL_CTRL_SET_MIN_PROTO_VERSION   123
private const val SSL_CTRL_SET_TLSEXT_HOSTNAME: Int = 55
private const val TLSEXT_NAMETYPE_HOST_NAME: Long = 0
private const val SSL_CTRL_SET_MIN_PROTO_VERSION: Int = 123

internal actual suspend fun performNativeTlsHandshake(
	connection: Connection,
	coroutineContext: CoroutineContext,
	selectorManager: SelectorManager?,
	config: NativeTlsConfigBuilder,
): NativeTlsConnection {
	val selectable =
		connection.socket as? Selectable
			?: error("Ktor Connection.socket is not Selectable on this target; cannot extract fd for SSL_set_fd")
	val fd = selectable.descriptor
	logger.trace { "starting TLS handshake on fd=$fd serverName=${config.serverName}" }

	// Stop Ktor's read/write pumps so they don't race with SSL_read / SSL_write on the fd.
	// Cancellation (not graceful close) is intentional: no plaintext bytes are pending.
	connection.input.cancel(null)
	connection.output.cancel(null)

	val ownsSelector = selectorManager == null
	val selector = selectorManager ?: SelectorManager(coroutineContext)

	val ctx = SSL_CTX_new(TLS_client_method()) ?: throw TlsException("SSL_CTX_new failed")
	try {
		configureContext(ctx, config)

		val ssl = SSL_new(ctx) ?: throw TlsException("SSL_new failed")
		try {
			if (SSL_set_fd(ssl, fd) != 1) {
				throw TlsException("SSL_set_fd failed for descriptor $fd")
			}
			configureSsl(ssl, config)

			val engine = LinuxSslEngine(ssl, ctx, selectable, selector)
			engine.handshake()
			logger.trace { "TLS handshake complete on fd=$fd" }

			return startAppDataPumps(engine, coroutineContext, ownsSelector, selector)
		} catch (cause: Throwable) {
			SSL_free(ssl)
			throw cause
		}
	} catch (cause: Throwable) {
		SSL_CTX_free(ctx)
		if (ownsSelector) selector.close()
		throw cause
	}
}

private fun configureContext(
	ctx: CPointer<SSL_CTX>,
	config: NativeTlsConfigBuilder,
) {
	// SSL_CTX_set_min_proto_version is a macro over SSL_CTX_ctrl — call the underlying
	// function directly so the link doesn't depend on a non-existent symbol.
	if (SSL_CTX_ctrl(ctx, SSL_CTRL_SET_MIN_PROTO_VERSION, TLS1_2_VERSION, null) != 1L) {
		throw TlsException("SSL_CTX_ctrl(SET_MIN_PROTO_VERSION) failed")
	}
	if (config.trustAnchorsDer.isEmpty()) {
		if (SSL_CTX_set_default_verify_paths(ctx) != 1) {
			throw TlsException("SSL_CTX_set_default_verify_paths failed")
		}
	} else {
		val store = X509_STORE_new() ?: throw TlsException("X509_STORE_new failed")
		var transferred = false
		try {
			for (anchorDer in config.trustAnchorsDer) {
				val anchor = parseDerCert(anchorDer)
				val added = X509_STORE_add_cert(store, anchor)
				X509_free(anchor)
				if (added != 1) {
					throw TlsException("X509_STORE_add_cert failed")
				}
			}
			// SSL_CTX_set_cert_store transfers ownership of `store` to `ctx`.
			SSL_CTX_set_cert_store(ctx, store)
			transferred = true
		} finally {
			if (!transferred) {
				io.natskt.tls.openssl
					.X509_STORE_free(store)
			}
		}
	}
}

private fun configureSsl(
	ssl: CPointer<SSL>,
	config: NativeTlsConfigBuilder,
) {
	val mode = if (config.verifyCertificates) SSL_VERIFY_PEER else SSL_VERIFY_NONE
	SSL_set_verify(ssl, mode, null)

	val serverName = config.serverName ?: return
	if (config.verifyCertificates) {
		if (SSL_set1_host(ssl, serverName) != 1) {
			throw TlsException("SSL_set1_host('$serverName') failed")
		}
	}
	// SNI: SSL_set_tlsext_host_name is a macro wrapping SSL_ctrl. OpenSSL copies the hostname
	// internally so the C buffer only needs to live for the call.
	memScoped {
		val rc =
			SSL_ctrl(
				ssl,
				SSL_CTRL_SET_TLSEXT_HOSTNAME,
				TLSEXT_NAMETYPE_HOST_NAME,
				serverName.cstr.ptr,
			)
		if (rc != 1L) throw TlsException("SSL_ctrl(SET_TLSEXT_HOSTNAME) failed: rc=$rc")
	}
}

private fun startAppDataPumps(
	engine: LinuxSslEngine,
	coroutineContext: CoroutineContext,
	ownsSelector: Boolean,
	selector: SelectorManager,
): NativeTlsConnection {
	val scope = CoroutineScope(coroutineContext)
	val appInput = ByteChannel(autoFlush = true)
	val appOutput = ByteChannel(autoFlush = true)

	val readJob: Job =
		scope.launch {
			val buf = ByteArray(16384)
			try {
				while (true) {
					val n = engine.read(buf, 0, buf.size)
					if (n <= 0) break
					appInput.writeFully(buf, 0, n)
					appInput.flush()
				}
			} catch (cause: Throwable) {
				appInput.cancel(cause)
				return@launch
			}
			appInput.flushAndClose()
		}

	val writeJob: Job =
		scope.launch {
			val buf = ByteArray(16384)
			try {
				while (true) {
					val n = appOutput.readAvailable(buf, 0, buf.size)
					if (n == -1) break
					if (n == 0) continue
					engine.write(buf, 0, n)
				}
			} catch (cause: Throwable) {
				appOutput.cancel(cause)
			}
		}

	return NativeTlsConnection(
		input = appInput,
		output = appOutput,
		closer = {
			try {
				engine.shutdown()
			} finally {
				readJob.cancelAndJoin()
				writeJob.cancelAndJoin()
				engine.close()
				if (ownsSelector) selector.close()
			}
		},
	)
}

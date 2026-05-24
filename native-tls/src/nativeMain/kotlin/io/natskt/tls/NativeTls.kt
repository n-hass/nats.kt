@file:OptIn(ExperimentalForeignApi::class)

package io.natskt.tls

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.Selectable
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.tls.TlsException
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.read
import io.ktor.utils.io.write
import io.natskt.tls.internal.IsolatedFdSelectable
import io.natskt.tls.internal.SslEngine
import io.natskt.tls.internal.configurePlatformTrust
import io.natskt.tls.openssl.BIO_NOCLOSE
import io.natskt.tls.openssl.BIO_new_fd
import io.natskt.tls.openssl.SSL
import io.natskt.tls.openssl.SSL_CTRL_SET_MIN_PROTO_VERSION
import io.natskt.tls.openssl.SSL_CTRL_SET_TLSEXT_HOSTNAME
import io.natskt.tls.openssl.SSL_CTX_ctrl
import io.natskt.tls.openssl.SSL_CTX_free
import io.natskt.tls.openssl.SSL_CTX_new
import io.natskt.tls.openssl.SSL_VERIFY_NONE
import io.natskt.tls.openssl.SSL_VERIFY_PEER
import io.natskt.tls.openssl.SSL_ctrl
import io.natskt.tls.openssl.SSL_free
import io.natskt.tls.openssl.SSL_new
import io.natskt.tls.openssl.SSL_set1_host
import io.natskt.tls.openssl.SSL_set_bio
import io.natskt.tls.openssl.SSL_set_verify
import io.natskt.tls.openssl.TLS1_2_VERSION
import io.natskt.tls.openssl.TLSEXT_NAMETYPE_host_name
import io.natskt.tls.openssl.TLS_client_method
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.dup
import platform.posix.dup2
import platform.posix.errno
import platform.posix.open
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger("NativeTls")

// Must match kotlinx-io's `Segment.SIZE`. the maximum minimumCapacity accepted by
// `Buffer.writableSegment`. Larger values would trip its internal `require`.
private const val SSL_READ_CHUNK = 8192

public suspend fun Connection.nativeTls(
	coroutineContext: CoroutineContext,
	block: NativeTlsConfigBuilder.() -> Unit = {},
): NativeTlsConnection {
	val config = NativeTlsConfigBuilder().apply(block).build()
	return performNativeTlsHandshake(this, coroutineContext, selectorManager = null, config)
}

/**
 * Wraps the existing TCP [connection] in TLS by `dup`ing its fd into a private one we own outright,
 * neutralising Ktor's view of the original fd, then handing the duped fd to an `SSL*` via
 * `SSL_set_fd`.
 *
 * The dup dance is necessary because Ktor's CIO reader/writer pumps call
 * `shutdown(originalFd, SHUT_RD/SHUT_WR)` via `invokeOnCompletion` when their channels are
 * cancelled. Without isolation that shutdown reaches the underlying file description and
 * subsequent SSL writes fail with `EPIPE`. After we `dup2` `/dev/null` over `originalFd`, the
 * pump-cleanup `shutdown` is a no-op on the wrong file description and the TCP connection (now
 * only referenced by the duped fd) is untouched.
 *
 * Platform-specific trust evaluation (system roots vs. caller-supplied anchors) is delegated to
 * [configurePlatformTrust]; everything else — record I/O, key schedule, version negotiation — is
 * OpenSSL's responsibility.
 */
internal suspend fun performNativeTlsHandshake(
	connection: Connection,
	coroutineContext: CoroutineContext,
	selectorManager: SelectorManager?,
	config: NativeTlsConfig,
): NativeTlsConnection {
	val originalSelectable =
		connection.socket as? Selectable
			?: error("Ktor Connection.socket is not Selectable on this target; cannot extract fd for SSL_set_fd")
	val originalFd = originalSelectable.descriptor
	logger.trace { "starting TLS handshake on fd=$originalFd serverName=${config.serverName}" }

	val ownsSelector = selectorManager == null
	val selector = selectorManager ?: SelectorManager(coroutineContext)

	// Pre-open /dev/null so the race window between `dup` (we now share the socket fd with Ktor's
	// pumps) and `dup2` (Ktor's view points at /dev/null) collapses to a single `dup2` syscall.
	// Any pump activity that gets dispatched inside that window can still touch the live socket;
	// keeping it to one syscall is the tightest we can get without a Ktor API to quiesce the pumps.
	val devNull = open("/dev/null", O_RDWR)
	if (devNull < 0) {
		if (ownsSelector) selector.close()
		throw TlsException("open(/dev/null) failed errno=$errno")
	}

	val ownedFd = dup(originalFd)
	if (ownedFd < 0) {
		close(devNull)
		if (ownsSelector) selector.close()
		throw TlsException("dup(originalFd=$originalFd) failed errno=$errno")
	}

	if (dup2(devNull, originalFd) < 0) {
		close(ownedFd)
		close(devNull)
		if (ownsSelector) selector.close()
		throw TlsException("dup2(/dev/null -> fd=$originalFd) failed errno=$errno")
	}
	close(devNull)

	// Drain the output channel deliberately *after* dup2: any bytes the caller had queued get
	// pumped to /dev/null rather than leaking on the wire as plaintext. The writer pump's
	// invokeOnCompletion runs `shutdown(originalFd, SHUT_WR)`, which is now harmless because
	// originalFd points at /dev/null — not the socket, which only ownedFd still references.
	connection.output.flushAndClose()
	connection.input.cancel(null)

	val sslSelectable = IsolatedFdSelectable(ownedFd)
	val ctx =
		SSL_CTX_new(TLS_client_method())
			?: run {
				close(ownedFd)
				if (ownsSelector) selector.close()
				throw TlsException("SSL_CTX_new failed")
			}
	val trustDisposer: () -> Unit
	try {
		if (SSL_CTX_ctrl(ctx, SSL_CTRL_SET_MIN_PROTO_VERSION, TLS1_2_VERSION.toLong(), null) != 1L) {
			throw TlsException("SSL_CTX_ctrl(SET_MIN_PROTO_VERSION) failed")
		}
		trustDisposer = configurePlatformTrust(ctx, config)
	} catch (cause: Throwable) {
		SSL_CTX_free(ctx)
		close(ownedFd)
		if (ownsSelector) selector.close()
		throw cause
	}

	val ssl =
		try {
			SSL_new(ctx) ?: throw TlsException("SSL_new failed")
		} catch (cause: Throwable) {
			trustDisposer()
			SSL_CTX_free(ctx)
			close(ownedFd)
			if (ownsSelector) selector.close()
			throw cause
		}

	try {
		val bio = BIO_new_fd(ownedFd, BIO_NOCLOSE) ?: throw TlsException("BIO_new_fd failed for descriptor $ownedFd")
		SSL_set_bio(ssl, bio, bio)
		configureSsl(ssl, config)

		val engine = SslEngine(ssl, ctx, sslSelectable, selector, onClose = trustDisposer)
		engine.handshake()
		logger.trace { "TLS handshake complete on fd=$ownedFd" }

		return startAppDataPumps(engine, coroutineContext, ownsSelector, selector)
	} catch (cause: Throwable) {
		SSL_free(ssl)
		SSL_CTX_free(ctx)
		trustDisposer()
		selector.notifyClosed(sslSelectable)
		if (ownsSelector) selector.close()
		throw cause
	}
}

private fun configureSsl(
	ssl: CPointer<SSL>,
	config: NativeTlsConfig,
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
				TLSEXT_NAMETYPE_host_name.toLong(),
				serverName.cstr.ptr,
			)
		if (rc != 1L) throw TlsException("SSL_ctrl(SET_TLSEXT_HOSTNAME) failed: rc=$rc")
	}
}

private fun startAppDataPumps(
	engine: SslEngine,
	coroutineContext: CoroutineContext,
	ownsSelector: Boolean,
	selector: SelectorManager,
): NativeTlsConnection {
	val scope = CoroutineScope(coroutineContext)
	val appInput = ByteChannel(autoFlush = true)
	val appOutput = ByteChannel(autoFlush = true)

	// Hoisted out of the loop so the closure is allocated once per connection, not per iteration.
	val decryptIntoChannelBuffer: (ByteArray, Int, Int) -> Int = { array, start, end ->
		engine.readNonBlocking(array, start, end - start)
	}
	val writeFromChannelBuffer: suspend (ByteArray, Int, Int) -> Int = { array, start, endExclusive ->
		engine.write(array, start, endExclusive - start)
	}

	val readJob: Job =
		scope.launch {
			try {
				readLoop@ while (true) {
					// Capacity is bounded by `Segment.SIZE` (8192) — kotlinx-io's writableSegment
					// `require`s minimumCapacity <= Segment.SIZE. SSL_read returns at most the
					// requested length per call, so we just loop again if more is buffered in OpenSSL.
					val n = appInput.write(SSL_READ_CHUNK, decryptIntoChannelBuffer)
					when {
						n > 0 -> Unit // write() already auto-flushed
						engine.isReadEof -> break@readLoop
						else -> engine.awaitReadReady()
					}
				}
			} catch (cause: Throwable) {
				appInput.cancel(cause)
				return@launch
			}
			appInput.flushAndClose()
		}

	val writeJob: Job =
		scope.launch {
			try {
				while (true) {
					val n = appOutput.read(writeFromChannelBuffer)
					if (n == -1) break
				}
			} catch (cause: Throwable) {
				appOutput.cancel(cause)
			}
		}

	return NativeTlsConnection(
		input = appInput,
		output = appOutput,
		closer = {
			// SSL_shutdown is a writer-side operation. OpenSSL allows one reader + one writer
			// concurrently on the same SSL*, but two concurrent writers is undefined behavior -
			// so writeJob must be fully joined before SSL_shutdown fires. With a
			// multi-threaded Dispatchers, i.e. IO, writeJob can otherwise still be inside
			// SSL_write on a worker thread while the closer runs.
			// readJob (SSL_read) is safe to leave running across SSL_shutdown and is joined after.
			//
			// NonCancellable wraps the whole sequence so an outer cancellation can't skip
			// writeJob.cancelAndJoin() and leave SSL_free racing an in-flight SSL_write.
			withContext(NonCancellable) {
				try {
					writeJob.cancelAndJoin()
					engine.shutdown()
					readJob.cancelAndJoin()
				} finally {
					engine.close()
					if (ownsSelector) selector.close()
				}
			}
		},
	)
}

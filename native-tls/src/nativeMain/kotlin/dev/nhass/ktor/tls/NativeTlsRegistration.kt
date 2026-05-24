@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.nhass.ktor.tls

import dev.nhass.ktor.tls.internal.NativeTlsTransportAdapter
import io.natskt.tls.spi.NativeTlsRegistrar
import platform.posix.SIGPIPE
import platform.posix.SIG_IGN
import platform.posix.signal

@OptIn(ExperimentalStdlibApi::class)
@Suppress("DEPRECATION", "UNUSED")
@EagerInitialization
private val registerNativeTls: Unit =
	run {
		// OpenSSL writes via `write(2)` on the raw fd; when the peer closes mid-write the kernel
		// raises SIGPIPE, which kills the process unless the signal is masked. Ktor's native
		// sockets don't set SO_NOSIGPIPE, so install a process-wide SIG_IGN once. SSL_write
		// then surfaces EPIPE via SSL_ERROR_SYSCALL through the normal error path.
		signal(SIGPIPE, SIG_IGN)
		NativeTlsRegistrar.upgrader = { rawConnection, tlsConfig, serverName, coroutineContext, selectorManager ->
			val config =
				NativeTlsConfigBuilder()
					.apply {
						this.serverName = serverName
						verifyCertificates = !tlsConfig.acceptAnyServerCertificate
						trustAnchorsDer = tlsConfig.caCertificatesDer
						if (tlsConfig.hasClientCertificate) {
							clientCertificateChainDer = tlsConfig.clientCertificateChainDer
							clientPrivateKeyDer = tlsConfig.clientPrivateKeyDer
							clientPrivateKeyAlgorithm = tlsConfig.clientPrivateKeyAlgorithm
						}
					}.build()
			val tls = performNativeTlsHandshake(rawConnection, coroutineContext, selectorManager, config)
			NativeTlsTransportAdapter(rawConnection, tls, coroutineContext)
		}
	}

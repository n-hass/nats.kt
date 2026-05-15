package io.natskt.tls

import io.natskt.tls.internal.NativeTlsTransportAdapter
import io.natskt.tls.spi.NativeTlsRegistrar

@OptIn(ExperimentalStdlibApi::class)
@Suppress("DEPRECATION", "UNUSED")
@EagerInitialization
private val registerNativeTls: Unit =
	run {
		NativeTlsRegistrar.upgrader = { rawConnection, tlsConfig, serverName, coroutineContext ->
			if (tlsConfig.hasClientCertificate) {
				throw UnsupportedOperationException(
					"Mutual TLS (clientCertificate) is not yet supported on Kotlin/Native targets. " +
						"Use a JVM target or a WebSocket transport on a platform whose Ktor engine supports it.",
				)
			}
			val tls =
				rawConnection.nativeTls(coroutineContext) {
					this.serverName = serverName
					verifyCertificates = !tlsConfig.acceptAnyServerCertificate
					trustAnchorsDer = tlsConfig.caCertificatesDer
				}
			NativeTlsTransportAdapter(rawConnection, tls, coroutineContext)
		}
	}

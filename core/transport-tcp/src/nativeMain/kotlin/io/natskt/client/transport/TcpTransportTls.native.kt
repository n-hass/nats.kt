package io.natskt.client.transport

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.sockets.connection
import io.ktor.network.tls.tls
import io.natskt.tls.spi.NativeTlsRegistrar

private val logger = KotlinLogging.logger("TcpTransportTls")

internal actual suspend fun performTlsUpgrade(transport: TcpTransport): Transport {
	val upgrade = NativeTlsRegistrar.upgrader

	if (upgrade == null) {
		logger.error {
			"Defaulting to Ktor TLS, which is not yet supported on Kotlin/Native targets and will likely fail.\n" +
				"To use an experimental TLS implementation for Kotlin/Native, add `implementation(\"io.github.n-hass:natskt-native-tls:\$version\")` to your build."
		}

		val tlsSocket =
			transport.inner.tls(transport.context) {
				serverName = transport.serverName
			}

		return TcpTransport(
			tlsSocket.connection(),
			transport.context,
			transport.selectorManager,
			transport.serverName,
			transport.tlsConfig,
		)
	}

	return upgrade(transport.inner, transport.tlsConfig, transport.serverName, transport.context)
}

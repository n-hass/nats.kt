package io.natskt.tls.spi

import io.ktor.network.sockets.Connection
import io.natskt.client.TlsConfig
import io.natskt.client.transport.Transport
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

/**
 * Plug-in registrar for native TLS upgrade.
 *
 * The `:native-tls` artifact, when present on the link line, registers its upgrader here via
 * `@EagerInitialization`
 */
public object NativeTlsRegistrar {
	@Volatile
	public var upgrader: (
		suspend (
			rawConnection: Connection,
			tlsConfig: TlsConfig,
			serverName: String?,
			coroutineContext: CoroutineContext,
		) -> Transport
	)? = null
}

package io.natskt.tls.spi

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.natskt.client.TlsConfig
import io.natskt.client.transport.Transport
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

/**
 * Plug-in registrar for native TLS upgrade.
 *
 * The `:native-tls` artifact, when present on the link line, registers its upgrader here via
 * `@EagerInitialization`. The [SelectorManager] is the same one driving [rawConnection], so the
 * upgrader can register the underlying file descriptor with it for non-blocking TLS I/O without
 * spawning a second event loop.
 */
public object NativeTlsRegistrar {
	@Volatile
	public var upgrader: (
		suspend (
			rawConnection: Connection,
			tlsConfig: TlsConfig,
			serverName: String?,
			coroutineContext: CoroutineContext,
			selectorManager: SelectorManager,
		) -> Transport
	)? = null
}

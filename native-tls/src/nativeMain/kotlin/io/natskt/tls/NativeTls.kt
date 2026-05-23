package io.natskt.tls

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import kotlin.coroutines.CoroutineContext

public suspend fun Connection.nativeTls(
	coroutineContext: CoroutineContext,
	block: NativeTlsConfigBuilder.() -> Unit = {},
): NativeTlsConnection {
	val config = NativeTlsConfigBuilder().apply(block)
	return performNativeTlsHandshake(this, coroutineContext, selectorManager = null, config)
}

/**
 * Platform-specific TLS engine entry point.
 *
 * The Linux actual binds an `SSL*` to the connection's file descriptor and uses [selectorManager]
 * (when provided) to drive non-blocking handshake and I/O — passing `null` makes the Linux
 * implementation create its own selector, which is acceptable for the public extension but
 * wasteful in the SPI upgrader path.
 *
 * The Apple actual closes the inbound [Connection]'s socket and opens a fresh
 * `nw_connection_t` with TLS parameters; [selectorManager] is unused there because
 * Network.framework manages its own dispatch.
 */
internal expect suspend fun performNativeTlsHandshake(
	connection: Connection,
	coroutineContext: CoroutineContext,
	selectorManager: SelectorManager?,
	config: NativeTlsConfigBuilder,
): NativeTlsConnection

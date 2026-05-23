package io.natskt.client.transport

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.CoroutineScope

public interface Transport : CoroutineScope {
	public val isClosed: Boolean

	/**
	 * Bytes being written to the socket by the NATS server.
	 */
	public val incoming: ByteReadChannel

	/**
	 * Close the connection.
	 */
	public suspend fun close()

	/**
	 * Upgrade the connection to a TLS connection if needed.
	 */
	public suspend fun upgradeTLS(): Transport

	/**
	 * When `true`, the value returned from [upgradeTLS] is a freshly-reconnected transport whose
	 * server has not yet been observed by the caller — a new INFO frame will appear on [incoming]
	 * and MUST be consumed before sending any client operation. This happens on Apple targets
	 * where [Network.framework](https://developer.apple.com/documentation/network) cannot adopt an
	 * existing socket fd; the TLS upgrade is implemented as a close-and-reconnect to the same
	 * peer.
	 *
	 * On Linux and JVM the original socket is wrapped in place and no new INFO is sent, so this
	 * stays `false`.
	 */
	public val tlsUpgradeReopened: Boolean get() = false

	/**
	 *
	 */
	public suspend fun write(block: suspend (ByteWriteChannel) -> Unit)

	public suspend fun flush()
}

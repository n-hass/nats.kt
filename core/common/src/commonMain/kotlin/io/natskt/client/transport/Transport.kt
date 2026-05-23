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
	 *
	 */
	public suspend fun write(block: suspend (ByteWriteChannel) -> Unit)

	public suspend fun flush()
}

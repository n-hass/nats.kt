package io.natskt.client

import io.natskt.api.Credentials
import io.natskt.api.internal.OperationSerializer
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.NUID
import kotlinx.coroutines.CoroutineScope

internal data class ClientConfiguration(
	/**
	 * Servers to possibly connect to.
	 */
	val servers: List<NatsServerAddress>,
	/**
	 * either a []
	 */
	val transportFactory: TransportFactory,
	val credentials: Credentials?,
	/**
	 *
	 */
	val inboxPrefix: String,
	/**
	 *
	 */
	internal val parser: OperationSerializer,
	/**
	 * The max number of reconnects for a single server.
	 */
	val maxReconnects: Int?,
	val connectTimeoutMs: Long,
	val reconnectDebounceMs: Long,
	val maxControlLineBytes: Int,
	/**
	 * Maximum number of bytes to queue before forcing a transport flush.
	 */
	val writeBufferLimitBytes: Int,
	/**
	 * Periodic flush cadence for the outbound writer job in milliseconds.
	 */
	val writeFlushIntervalMs: Long,
	val tlsRequired: Boolean,
	/**
	 * The NUID generator to use.
	 */
	val nuid: NUID,
	/**
	 * The coroutine scope that the client's connections and parsing runs on
	 */
	val scope: CoroutineScope,
	/**
	 * Whether the client should cancel the underlying scope on disconnect.
	 */
	val ownsScope: Boolean,
) {
	/**
	 * The length of an inbox created using [createInbox]
	 */
	val inboxLength: Int get() = inboxPrefix.length + 22

	/**
	 *
	 */
	public fun createInbox(): String = inboxPrefix + nuid.next()
}

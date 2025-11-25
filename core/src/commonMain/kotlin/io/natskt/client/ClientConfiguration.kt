package io.natskt.client

import io.natskt.api.Credentials
import io.natskt.api.internal.OperationSerializer
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.NUID
import kotlinx.coroutines.CoroutineScope

internal data class ClientConfiguration(
	val servers: List<NatsServerAddress>,
	val transportFactory: TransportFactory,
	val credentials: Credentials?,
	val inboxPrefix: String,
	internal val parser: OperationSerializer,
	val maxReconnects: Int?,
	val connectTimeoutMs: Long,
	val reconnectDebounceMs: Long,
	val maxControlLineBytes: Int,
	val writeBufferLimitBytes: Int,
	val writeFlushIntervalMs: Long,
	val tlsRequired: Boolean,
	val maxParallelRequests: Int?,
	val nuid: NUID,
	val scope: CoroutineScope,
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

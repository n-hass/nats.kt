package io.natskt.api

import io.natskt.api.internal.InternalNatsApi
import io.natskt.client.ByteMessageBuilder
import io.natskt.client.ClientConfiguration
import io.natskt.client.NatsClientImpl
import io.natskt.client.StringMessageBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

public interface NatsClient {
	/**
	 * [ConnectionState] shows the current [ConnectionPhase] the connection is in,
	 * as well as statistics about the connection itself
	 */
	public val connectionState: StateFlow<ConnectionState>

	/**
	 * The subscriptions that have been created with this [NatsClient].
	 */
	public val subscriptions: Map<String, Subscription>

	/**
	 * Activates the client's connection. Suspends until active or timeout.
	 * Returns [Result.success] when the connection was successful, or a failure
	 */
	public suspend fun connect(): Result<Unit>

	/**
	 * Deactivates the client's connection, closing the transport
	 */
	public suspend fun disconnect()

	/**
	 * Create a new [Subscription]
	 */
	public suspend fun subscribe(
		subject: String,
		queueGroup: String? = null,
		eager: Boolean = true,
		replayBuffer: Int = 0,
		unsubscribeOnLastCollector: Boolean = true,
		scope: CoroutineScope? = null,
	): Subscription

	/**
	 * Create a new [Subscription]
	 */
	public suspend fun subscribe(
		subject: Subject,
		queueGroup: String? = null,
		eager: Boolean = true,
		replayBuffer: Int = 0,
		unsubscribeOnLastCollector: Boolean = true,
		scope: CoroutineScope? = null,
	): Subscription

	public suspend fun publish(
		subject: String,
		message: ByteArray?,
		headers: Map<String, List<String>>? = null,
		replyTo: String? = null,
	)

	public suspend fun publish(
		subject: Subject,
		message: ByteArray?,
		headers: Map<String, List<String>>? = null,
		replyTo: Subject? = null,
	)

	public suspend fun publish(message: Message)

	public suspend fun publishBytes(byteMessageBlock: ByteMessageBuilder.() -> Unit)

	public suspend fun publishString(stringMessageBlock: StringMessageBuilder.() -> Unit)

	public suspend fun request(
		subject: String,
		message: ByteArray?,
		headers: Map<String, List<String>>? = null,
		timeoutMs: Long = 5000,
	): Message

	/**
	 * Trigger a ping. The round-trip-time will then be updated in the client's [connectionState]
	 */
	public suspend fun ping(): Unit

	/**
	 * Initiate a protocol drain. Unsubscribes on all subscriptions and flushes the transport,
	 * but does NOT close transport; returns when drained or timeout.
	 */
	public suspend fun drain(timeout: Duration): Unit

	/**
	 * Force flushing the transport
	 */
	public suspend fun flush(): Unit

	@InternalNatsApi
	public fun nextInbox(): String

	@InternalNatsApi
	public val scope: CoroutineScope

	public companion object {
		internal operator fun invoke(config: ClientConfiguration): NatsClientImpl = NatsClientImpl(config)
	}
}

package io.natskt.api

import io.natskt.client.ByteMessageBuilder
import io.natskt.client.ClientConfiguration
import io.natskt.client.NatsClientImpl
import io.natskt.client.StringMessageBuilder
import kotlinx.coroutines.CoroutineScope

public interface NatsClient {
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
	 * Deactivates the client's connection
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
		message: ByteArray,
		headers: Map<String, List<String>>? = null,
		replyTo: String? = null,
	)

	public suspend fun publish(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>? = null,
		replyTo: Subject? = null,
	)

	public suspend fun publish(message: Message)

	public suspend fun publishBytes(byteMessageBlock: ByteMessageBuilder.() -> Unit)

	public suspend fun publishString(stringMessageBlock: StringMessageBuilder.() -> Unit)

	public suspend fun request(
		subject: String,
		message: ByteArray,
		headers: Map<String, List<String>>? = null,
		timeoutMs: Long = 5000,
	): Message

	public companion object {
		internal operator fun invoke(config: ClientConfiguration): NatsClientImpl = NatsClientImpl(config)
	}
}

package io.natskt.api

import io.natskt.client.ByteMessageBuilder
import io.natskt.client.ClientConfiguration
import io.natskt.client.NatsClientImpl
import io.natskt.client.StringMessageBuilder
import io.natskt.internal.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

public interface NatsClient {
	/**
	 * The subscriptions that have been created with this [NatsClient].
	 */
	public val subscriptions: Map<String, Subscription>

	/**
	 *
	 */
	public suspend fun connect()

	/**
	 *
	 */
	public suspend fun subscribe(
		subject: Subject,
		queueGroup: String? = null,
	): Subscription

	public suspend fun publish(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>? = null,
		replyTo: Subject? = null,
	): Unit

	public suspend fun publish(message: Message): Unit

	public suspend fun publish(messageBlock: ByteMessageBuilder.() -> Unit): Unit

	public suspend fun publish(messageBlock: StringMessageBuilder.() -> Unit): Unit

	public suspend fun request(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>? = null,
		launchIn: CoroutineScope?,
	): Deferred<Message>

	public companion object {
		internal operator fun invoke(config: ClientConfiguration) = NatsClientImpl(config)
	}
}

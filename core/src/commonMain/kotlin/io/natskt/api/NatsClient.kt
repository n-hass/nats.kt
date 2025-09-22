package io.natskt.api

import io.natskt.client.ByteMessageBuilder
import io.natskt.client.ClientConfiguration
import io.natskt.client.NatsClientImpl
import io.natskt.client.StringMessageBuilder
import io.natskt.internal.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred

public object ByteMessageBlockTag

public object StringMessageBlockTag

public interface NatsClient {
	/**
	 * The subscriptions that have been created with this [NatsClient].
	 */
	public val subscriptions: Map<String, Subscription>

	/**
	 *
	 */
	public suspend fun connect(scope: CoroutineScope? = null)

	/**
	 *
	 */
	public suspend fun subscribe(
		subject: String,
		queueGroup: String? = null,
		eager: Boolean = true,
		replayBuffer: Int = 0,
		unsubscribeOnLastCollector: Boolean = true,
		scope: CoroutineScope? = null,
	): Subscription

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
	): Unit

	public suspend fun publish(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>? = null,
		replyTo: Subject? = null,
	): Unit

	public suspend fun publish(message: Message): Unit

	public suspend fun publishBytes(byteMessageBlock: ByteMessageBuilder.() -> Unit): Unit

	public suspend fun publishString(stringMessageBlock: StringMessageBuilder.() -> Unit): Unit

	public suspend fun request(
		subject: String,
		message: ByteArray,
		headers: Map<String, List<String>>? = null,
		timeoutMs: Long = 5000,
		launchIn: CoroutineScope? = null,
	): Deferred<Message>

	public companion object {
		internal operator fun invoke(config: ClientConfiguration) = NatsClientImpl(config)
	}
}

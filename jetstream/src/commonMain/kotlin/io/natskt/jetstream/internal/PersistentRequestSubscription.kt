package io.natskt.jetstream.internal

import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subscription
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.NUID
import io.natskt.jetstream.api.JetStreamClient
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@OptIn(InternalNatsApi::class)
public open class PersistentRequestSubscription(
	internal val js: JetStreamClient,
	internal val inboxSubscription: Subscription,
) : CanRequest,
	AutoCloseable {
	internal val inboxPrefix = inboxSubscription.subject.raw.dropLast(1)

	override val context: JetStreamContext
		get() = js.context

	internal fun nextRequestSubject() = inboxPrefix + NUID.nextSequence()

	override suspend fun request(
		subject: String,
		message: String?,
		headers: Map<String, List<String>>?,
		timeoutMs: Long,
	): Message {
		val replyTo = nextRequestSubject()
		return inboxSubscription.messages
			.filter { it.subject.raw == replyTo }
			.onStart {
				js.client.publish(subject, message?.encodeToByteArray(), headers, replyTo = replyTo)
			}.first()
	}

	@OptIn(InternalNatsApi::class)
	override fun close() {
		js.client.scope.launch { inboxSubscription.unsubscribe() }
	}

	internal companion object {
		@OptIn(InternalNatsApi::class)
		suspend fun newSubscription(client: NatsClient): Subscription = client.subscribe(client.nextInbox() + ".*", replayBuffer = 0, unsubscribeOnLastCollector = false, eager = true)
	}
}

package io.natskt.jetstream.internal

import io.natskt.api.NatsClient
import io.natskt.api.Subscription
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.NUID
import io.natskt.jetstream.api.ApiResponse
import io.natskt.jetstream.api.JetStreamApiResponse
import io.natskt.jetstream.api.JetStreamClient
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

internal abstract class PersistentRequestSubscription(
	val js: JetStreamClient,
	val inboxSubscription: Subscription,
) : AutoCloseable {
	val inboxPrefix = inboxSubscription.subject.raw.dropLast(1)

	fun nextRequestSubject() = inboxPrefix + NUID.nextSequence()

	suspend inline fun <reified T : JetStreamApiResponse> request(
		subject: String,
		message: String?,
		headers: Map<String, List<String>>? = null,
		timeoutMs: Long = 5000,
	): ApiResponse {
		val replyTo = nextRequestSubject()
		val response =
			inboxSubscription.messages
				.filter { it.subject.raw == replyTo }
				.onStart {
					js.client.publish(subject, message?.encodeToByteArray(), headers, replyTo = replyTo)
				}.first()
		return response.decode<T>()
	}

	@OptIn(InternalNatsApi::class)
	override fun close() {
		js.client.scope.launch { inboxSubscription.unsubscribe() }
	}

	companion object {
		@OptIn(InternalNatsApi::class)
		suspend fun newSubscription(client: NatsClient): Subscription = client.subscribe(client.nextInbox() + ".*", replayBuffer = 0, unsubscribeOnLastCollector = false, eager = true)
	}
}

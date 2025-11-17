package io.natskt.jetstream.internal

import io.natskt.api.JetStreamMessage
import io.natskt.api.Subscription
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.ConsumerPullRequest
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.consumer.PullConsumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

@OptIn(InternalNatsApi::class)
internal class PullConsumerImpl(
	val name: String,
	val streamName: String,
	js: JetStreamClient,
	inboxSubscription: Subscription,
	initialInfo: ConsumerInfo?,
	private val defaultTimeout: Long = 10_000_000_000, // 10 seconds in nanoseconds
) : PersistentRequestSubscription(js, inboxSubscription),
	PullConsumer {
	private var lastPullCode = 0
	private var lastPullBody = ""

	override val info = MutableStateFlow(initialInfo)

	override suspend fun updateConsumerInfo(): Result<ConsumerInfo> {
		val new = getConsumerInfo(streamName, name)
		new.onSuccess {
			info.value = it
		}
		return new
	}

	override suspend fun fetch(
		batch: Int,
		expires: Duration?,
		noWait: Boolean?,
		maxBytes: Int?,
		heartbeat: Duration?,
	): List<JetStreamMessage> {
		val code = batch.hashCode() + expires.hashCode() + noWait.hashCode() + maxBytes.hashCode() + heartbeat.hashCode()
		val body =
			if (code == lastPullCode) {
				lastPullBody
			} else {
				val body =
					wireJsonFormat.encodeToString(
						ConsumerPullRequest(
							expires = expires?.inWholeNanoseconds ?: (if (noWait == true) null else defaultTimeout),
							batch = batch,
							noWait = noWait,
							maxBytes = maxBytes,
							idleHeartbeat = heartbeat?.inWholeNanoseconds,
						),
					)
				lastPullCode = code
				lastPullBody = body
				body
			}

		val timeoutMillis = expires?.inWholeMilliseconds ?: 10_000
		val messages =
			withTimeoutOrNull(timeoutMillis) {
				inboxMessages
					.takeWhile { message ->
						val status = message.status
						status == null || status in 200..300
					}.take(batch)
					.onStart {
						publishMsgNext(streamName, name, body, nextRequestSubject())
					}.toCollection(ArrayList(batch))
			} ?: return emptyList()

		return messages.map {
			wrapJetstreamMessage(it, js)
		}
	}

	companion object {
		suspend operator fun invoke(
			name: String,
			streamName: String,
			js: JetStreamClient,
			defaultTimeout: Long = 10_000_000_000,
			initialInfo: ConsumerInfo?,
		): PullConsumerImpl =
			PullConsumerImpl(
				name = name,
				streamName = streamName,
				js = js,
				inboxSubscription = newSubscription(js.client),
				defaultTimeout = defaultTimeout,
				initialInfo = initialInfo,
			)
	}
}

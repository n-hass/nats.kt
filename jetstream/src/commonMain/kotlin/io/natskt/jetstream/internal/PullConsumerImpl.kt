package io.natskt.jetstream.internal

import io.natskt.api.JetStreamMessage
import io.natskt.api.Subscription
import io.natskt.internal.MessageInternal
import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.ConsumerPullRequest
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.consumer.PullConsumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

internal class PullConsumerImpl(
	val name: String,
	val streamName: String,
	js: JetStreamClient,
	inboxSubscription: Subscription,
	private val defaultTimeout: Long = 10_000_000_000, // 10 seconds in nanoseconds
	initialInfo: ConsumerInfo?,
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

		val messages =
			withTimeoutOrNull(expires?.inWholeMilliseconds ?: defaultTimeout) {
				inboxSubscription.messages
					.takeWhile {
						if (it.status != null && it.status !in 200..300) {
							return@takeWhile false
						}
						true
					}.take(batch)
					.onStart {
						pull(streamName, name, body, nextRequestSubject())
					}.toList()
			} ?: return emptyList()

		return messages.map {
			IncomingJetStreamMessage(
				original = it as MessageInternal,
				ackAction = { subject, body -> js.client.publish(subject, body) },
			)
		}
	}

	companion object {
		suspend fun invoke(
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

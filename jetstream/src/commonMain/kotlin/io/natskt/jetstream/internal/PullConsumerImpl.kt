package io.natskt.jetstream.internal

import io.natskt.api.JetStreamMessage
import io.natskt.api.Subscription
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.throwOnInvalidToken
import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.ConsumerPullRequest
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.JetStreamConnectivityException
import io.natskt.jetstream.api.JetStreamConsumerStateException
import io.natskt.jetstream.api.consumer.PullConsumer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
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
	init {
		name.throwOnInvalidToken()
		streamName.throwOnInvalidToken()
	}

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

		val replyTo = nextRequestSubject()
		val timeoutMillis = (expires?.inWholeMilliseconds ?: 10_000) + 500
		val collected = ArrayList<JetStreamMessage>(batch)

		withTimeoutOrNull(timeoutMillis) {
			inboxMessages
				.onStart { publishMsgNext(streamName, name, body, replyTo) }
				.takeWhile { msg ->
					// Drop status messages addressed to a previous fetch's replyTo.
					if (msg.status != null && msg.subject.raw != replyTo) return@takeWhile true

					// Matches nats.go pull.go fetch (lines 943-955): only 408 Timeout/
					// Interest Expired, 404 NoMessages, and 409 "exceeds maxbytes" are
					// silent terminations — every other status surfaces as an error.
					when (val status = msg.status) {
						null -> {
							collected.add(wrapJetstreamMessage(msg, js))
							collected.size < batch
						}
						// Pull-side heartbeats are watchdog-only; never replied to.
						HEARTBEAT_STATUS -> true
						in 200..299 -> false
						NO_MESSAGES_STATUS, REQUEST_TIMEOUT_STATUS -> false
						CONFLICT_STATUS -> {
							val desc = msg.statusDescription.orEmpty().lowercase()
							if (DESC_EXCEEDS_MAXBYTES in desc) {
								false
							} else {
								throw JetStreamConsumerStateException(status, msg.statusDescription)
							}
						}
						NO_RESPONDERS_STATUS ->
							throw JetStreamConnectivityException(status, msg.statusDescription)
						else ->
							throw JetStreamConsumerStateException(status, msg.statusDescription)
					}
				}.collect()
		}

		return collected
	}

	companion object {
		suspend operator fun invoke(
			name: String,
			streamName: String,
			js: JetStreamClient,
			defaultTimeout: Long = 10_000_000_000,
			initialInfo: ConsumerInfo?,
		): PullConsumerImpl {
			name.throwOnInvalidToken()
			streamName.throwOnInvalidToken()
			return PullConsumerImpl(
				name = name,
				streamName = streamName,
				js = js,
				inboxSubscription = newSubscription(js.client),
				defaultTimeout = defaultTimeout,
				initialInfo = initialInfo,
			)
		}
	}
}

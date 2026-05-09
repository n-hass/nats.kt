@file:OptIn(InternalNatsApi::class, ExperimentalTime::class)

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
import io.natskt.jetstream.api.consumer.ConsumeOptions
import io.natskt.jetstream.api.consumer.PullConsumer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

private const val NATS_PENDING_MESSAGES_HEADER = "Nats-Pending-Messages"

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
		group: String?,
		minPending: Long?,
		minAckPending: Long?,
	): List<JetStreamMessage> {
		val body =
			wireJsonFormat.encodeToString(
				ConsumerPullRequest(
					expires = expires?.inWholeNanoseconds ?: (if (noWait == true) null else defaultTimeout),
					batch = batch,
					noWait = noWait,
					maxBytes = maxBytes,
					idleHeartbeat = heartbeat?.inWholeNanoseconds,
					group = group,
					minPending = minPending,
					minAckPending = minAckPending,
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

					// only 408 Timeout/Interest Expired, 404 NoMessages, and 409 "exceeds maxbytes" are
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

	override fun consume(options: ConsumeOptions): Flow<JetStreamMessage> =
		channelFlow {
			require(options.batch >= 1) { "batch must be >= 1" }
			val threshold = options.thresholdMessages ?: ((options.batch + 1) / 2)
			require(threshold in 0 until options.batch) {
				"thresholdMessages must be in [0, batch) but was $threshold (batch=${options.batch})"
			}
			val heartbeat =
				options.idleHeartbeat
					?: minOf(options.expires.div(2), 30.seconds)
			require(heartbeat <= options.expires.div(2)) {
				"idleHeartbeat must be <= expires / 2"
			}

			val replyTo = nextRequestSubject()
			var pendingMsgs = 0
			var lastActivity = Clock.System.now()

			suspend fun issuePull(batchToFetch: Int) {
				val body =
					wireJsonFormat.encodeToString(
						ConsumerPullRequest(
							expires = options.expires.inWholeNanoseconds,
							batch = batchToFetch,
							maxBytes = options.maxBytes,
							idleHeartbeat = heartbeat.inWholeNanoseconds,
							group = options.group,
							minPending = options.minPending,
							minAckPending = options.minAckPending,
						),
					)
				publishMsgNext(streamName, name, body, replyTo)
				pendingMsgs += batchToFetch
			}

			fun maybeRefill() {
				if (pendingMsgs >= threshold) return
				val refill = options.batch - pendingMsgs
				if (refill <= 0) return
				launch { issuePull(refill) }
			}

			issuePull(options.batch)

			// If no inbound activity within 2× heartbeat, reissue the pull rather
			// than terminating; the previous pull is treated as dead.
			val watchdog =
				launch {
					val window = heartbeat * 2
					while (isActive) {
						delay(window)
						val sinceActivity = Clock.System.now() - lastActivity
						if (sinceActivity > window) {
							pendingMsgs = 0
							issuePull(options.batch)
							lastActivity = Clock.System.now()
						}
					}
				}

			try {
				inboxMessages
					.filter { msg -> msg.status == null || msg.subject.raw == replyTo }
					.collect { msg ->
						lastActivity = Clock.System.now()
						when (val status = msg.status) {
							null -> {
								send(wrapJetstreamMessage(msg, js))
								pendingMsgs--
								maybeRefill()
							}
							// Pull-side heartbeats are watchdog-only; lastActivity already updated.
							HEARTBEAT_STATUS -> Unit
							in 200..299 -> Unit
							NO_MESSAGES_STATUS, REQUEST_TIMEOUT_STATUS -> {
								pendingMsgs -= unfilledFromHeader(msg.headers)
								maybeRefill()
							}
							CONFLICT_STATUS -> {
								val desc = msg.statusDescription.orEmpty().lowercase()
								when {
									DESC_EXCEEDS_MAXBYTES in desc || "batch completed" in desc -> {
										pendingMsgs -= unfilledFromHeader(msg.headers)
										maybeRefill()
									}
									"leadership change" in desc -> {
										pendingMsgs = 0
										issuePull(options.batch)
									}
									else ->
										throw JetStreamConsumerStateException(status, msg.statusDescription)
								}
							}
							PIN_ID_MISMATCH_STATUS -> {
								pendingMsgs = 0
								issuePull(options.batch)
							}
							NO_RESPONDERS_STATUS ->
								throw JetStreamConnectivityException(status, msg.statusDescription)
							else ->
								throw JetStreamConsumerStateException(status, msg.statusDescription)
						}
					}
			} finally {
				watchdog.cancel()
			}
		}

	private fun unfilledFromHeader(headers: Map<String, List<String>>?): Int = headers?.get(NATS_PENDING_MESSAGES_HEADER)?.firstOrNull()?.toIntOrNull() ?: 0

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

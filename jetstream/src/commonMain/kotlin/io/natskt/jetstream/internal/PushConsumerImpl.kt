package io.natskt.jetstream.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ClosedWriteChannelException
import io.natskt.api.JetStreamMessage
import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subscription
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.throwOnInvalidToken
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.consumer.JetStreamHeartbeatException
import io.natskt.jetstream.api.consumer.PushConsumer
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val HEARTBEAT_STATUS = 100
private const val STALLED_HEADER = "Nats-Consumer-Stalled"

private val logger = KotlinLogging.logger { }

@OptIn(InternalNatsApi::class, ExperimentalTime::class)
internal class PushConsumerImpl(
	val name: String,
	val streamName: String,
	val subscription: Subscription,
	private val js: JetStreamClient,
	initialInfo: ConsumerInfo?,
) : PushConsumer {
	internal var heartbeatInterval: Duration?

	@Volatile internal var lastActivity: Instant? = null

	init {
		name.throwOnInvalidToken()
		streamName.throwOnInvalidToken()
		heartbeatInterval = initialInfo?.config?.idleHeartbeat
	}

	override val info = MutableStateFlow(initialInfo)

	override val messages: Flow<JetStreamMessage> =
		channelFlow {
			// TODO: cant test reliably, not enabling
// 			launch {
// 				heartbeatWatchdog(this@channelFlow)
// 			}
			subscription.messages
				.onEach {
					lastActivity = Clock.System.now()
				}.collect { msg ->
					when (msg.status) {
						HEARTBEAT_STATUS -> handleStatus(msg)
						else -> {
							if (msg.status == null) {
								send(wrapJetstreamMessage(msg, js))
							}
						}
					}
				}
		}

	override suspend fun updateConsumerInfo(): Result<ConsumerInfo> {
		val new = js.getConsumerInfo(streamName, name)
		new.onSuccess {
			info.value = it
			heartbeatInterval = it.config.idleHeartbeat
		}
		return new
	}

	override fun close() {
		js.client.scope.launch {
			try {
				subscription.unsubscribe()
			} catch (_: ClosedWriteChannelException) {
				// ignore if this runs on a closed connection
			} catch (_: ClosedSendChannelException) {
				// ignore if this runs on a closed connection
			}
		}
	}

	private suspend fun handleStatus(message: Message) {
		if (message.replyTo != null) {
			js.client.publish(message.replyTo!!.raw, null)
		} else if (message.headers?.get(STALLED_HEADER) != null) {
			message.headers?.get(STALLED_HEADER)?.firstOrNull()?.let {
				js.client.publish(it, null)
			}
		}
	}

	private suspend fun heartbeatWatchdog(scope: ProducerScope<*>) {
		while (scope.isActive) {
			val interval = heartbeatInterval
			if (interval == null || interval == Duration.ZERO) {
				delay(1000)
				continue
			}

			val timeout = interval * 3
			delay(timeout)

			val last = lastActivity
			val now = Clock.System.now()

			if (last == null) {
				continue
			}

			if (now - last > timeout) {
				try {
					subscription.unsubscribe()
				} catch (_: Exception) {
				}

				scope.channel.close(
					JetStreamHeartbeatException(
						"PushConsumer($streamName/$name) missed 2 heartbeats",
					),
				)
				return
			}
		}
	}

	companion object {
		suspend fun newSubscription(
			client: NatsClient,
			subject: String?,
			eager: Boolean = false,
		): Subscription {
			val subject = subject ?: client.nextInbox()
			return client.subscribe(
				subject = subject,
				queueGroup = null,
				eager = eager,
				replayBuffer = 1,
				unsubscribeOnLastCollector = false,
			)
		}
	}
}

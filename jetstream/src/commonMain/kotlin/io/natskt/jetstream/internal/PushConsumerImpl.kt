package io.natskt.jetstream.internal

import io.natskt.api.Message
import io.natskt.api.Subscription
import io.natskt.api.internal.InternalNatsApi
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.consumer.PushConsumer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

@OptIn(InternalNatsApi::class)
internal class PushConsumerImpl(
	val name: String,
	val streamName: String,
	val subscription: Subscription,
	private val js: JetStreamClient,
	initialInfo: ConsumerInfo?,
) : PushConsumer {
	override val info = MutableStateFlow(initialInfo)

	override val messages: Flow<Message> =
		channelFlow {
			subscription.messages.collect { msg ->
				when (msg.status) {
					FLOW_CONTROL_REQUEST_STATUS -> handleFlowControl(msg)
					IDLE_HEARTBEAT_STATUS -> handleIdleHeartbeat(msg)
					else -> {
						if (msg.status == null) {
							send(msg)
						}
					}
				}
			}
		}

	override suspend fun updateConsumerInfo(): Result<ConsumerInfo> {
		val new = js.getConsumerInfo(streamName, name)
		new.onSuccess {
			info.value = it
		}
		return new
	}

	override fun close() {
		js.client.scope.launch {
			subscription.unsubscribe()
		}
	}

	private suspend fun handleFlowControl(message: Message) {
		val replyTo = message.replyTo ?: return
		js.client.publish(replyTo, null, null, null)
	}

	private suspend fun handleIdleHeartbeat(message: Message) {
		val stalledSubject = message.headers?.get(NATS_CONSUMER_STALLED_HEADER)?.firstOrNull() ?: return
		js.client.publish(stalledSubject, null, null, null)
	}

	private companion object {
		private const val FLOW_CONTROL_REQUEST_STATUS = 409
		private const val IDLE_HEARTBEAT_STATUS = 100
		private const val NATS_CONSUMER_STALLED_HEADER = "Nats-Consumer-Stalled"
	}
}

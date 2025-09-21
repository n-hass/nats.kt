package io.natskt.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subscription
import io.natskt.api.internal.ClientOperation
import io.natskt.client.connection.ConnectionManager
import io.natskt.internal.Subject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger { }

internal class NatsClientImpl(
	val configuration: ClientConfiguration,
) : NatsClient {
	internal val connectionManager = ConnectionManager(configuration)

	override val subscriptions: Map<String, Subscription>
		get() = TODO("Not yet implemented")

	override suspend fun connect() {
		connectionManager.start()
		CoroutineScope(currentCoroutineContext()).launch {
			connectionManager.connectionStatus.collect {
				logger.debug { "Connection status change: $it" }
			}
		}
		CoroutineScope(currentCoroutineContext()).launch {
			connectionManager
		}
	}

	override suspend fun subscribe(
		subject: Subject,
		queueGroup: String?,
	): Subscription {
	}

	override suspend fun publish(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: Subject?,
	) {
		val op =
			if (headers == null) {
				ClientOperation.PubOp(
					subject = subject.raw,
					replyTo = replyTo?.raw,
					payload = message,
				)
			} else {
				ClientOperation.HPubOp(
					subject = subject.raw,
					replyTo = replyTo?.raw,
					headers = headers,
					payload = message,
				)
			}
		connectionManager.send(op)
	}

	override suspend fun publish(message: Message) {
		val op =
			if (message.headers == null) {
				ClientOperation.PubOp(
					subject = message.subject.raw,
					replyTo = message.replyTo?.raw,
					payload = message.data,
				)
			} else {
				ClientOperation.HPubOp(
					subject = message.subject.raw,
					replyTo = message.replyTo?.raw,
					headers = message.headers,
					payload = message.data,
				)
			}
		connectionManager.send(op)
	}

	override suspend fun publish(messageBlock: ByteMessageBuilder.() -> Unit) {
		val message = ByteMessageBuilder().apply { messageBlock() }.build()
		publish(message)
	}

	override suspend fun publish(messageBlock: StringMessageBuilder.() -> Unit) {
		val message = StringMessageBuilder().apply { messageBlock() }.build()
		publish(message)
	}

	override suspend fun request(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		launchIn: CoroutineScope?,
	): Deferred<Message> {
		val inbox = subscribe(configuration.createInbox())
		val scope = launchIn ?: CoroutineScope(currentCoroutineContext())
		publish(subject, message, headers)
		val result = CompletableDeferred<Message>()
		scope.launch {
			// suspend on collecting the inbox messages
		}
		return result
	}
}

package io.natskt.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import io.natskt.api.ConnectionPhase
import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subscription
import io.natskt.api.internal.ClientOperation
import io.natskt.api.internal.InternalSubscriptionHandler
import io.natskt.client.connection.ConnectionManagerImpl
import io.natskt.internal.Subject
import io.natskt.internal.SubscriptionImpl
import io.natskt.internal.connectionCoroutineDispatcher
import io.natskt.internal.from
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private val logger = KotlinLogging.logger { }

internal class NatsClientImpl(
	val configuration: ClientConfiguration,
) : NatsClient {
	private val clientScope = CoroutineScope(SupervisorJob() + connectionCoroutineDispatcher + CoroutineName("NatsClient"))

	private val _subscriptions = ConcurrentMap<String, InternalSubscriptionHandler>()
	override val subscriptions: Map<String, Subscription>
		get() = _subscriptions

	internal val connectionManager = ConnectionManagerImpl(configuration, _subscriptions)

	@OptIn(ExperimentalAtomicApi::class)
	private val sidAllocator = AtomicInt(1)

	override suspend fun connect(scope: CoroutineScope?) {
		connectionManager.start()
		val scope = scope ?: clientScope
		scope.launch {
			connectionManager.connectionStatus.collect {
				logger.debug { "Connection status change: $it" }
			}
		}

		withTimeoutOrNull(configuration.connectTimeoutMs) {
			connectionManager.connectionStatus
				.filter {
					it.phase == ConnectionPhase.Connected
				}.first()
		}
			?: connectionManager.stop()
	}

	@OptIn(ExperimentalAtomicApi::class)
	override suspend fun subscribe(
		subject: String,
		queueGroup: String?,
		eager: Boolean,
		replayBuffer: Int,
		unsubscribeOnLastCollector: Boolean,
		scope: CoroutineScope?,
	): Subscription = subscribe(Subject(subject), queueGroup, eager, replayBuffer, unsubscribeOnLastCollector, scope)

	@OptIn(ExperimentalAtomicApi::class)
	override suspend fun subscribe(
		subject: Subject,
		queueGroup: String?,
		eager: Boolean,
		replayBuffer: Int,
		unsubscribeOnLastCollector: Boolean,
		scope: CoroutineScope?,
	): Subscription {
		val sid = sidAllocator.fetchAndAdd(1).toString()
		require(_subscriptions[sid] == null) { "duplicate subscription ID: $sid" }

		suspend fun onStart(
			sub: SubscriptionImpl,
			sid: String,
			subject: String,
			queueGroup: String?,
		) {
			require(subscriptions[sid] == null) { "subscription is triggering onStart twice" }
			_subscriptions[sid] = sub
			connectionManager.send(
				ClientOperation.SubOp(
					sid = sid,
					subject = subject,
					queueGroup = queueGroup,
				),
			)
		}

		suspend fun onStop(
			sid: String,
			maxMsgs: Int?,
		) {
			_subscriptions[sid] ?: return
			_subscriptions.remove(sid)
			connectionManager.send(ClientOperation.UnSubOp(sid, maxMsgs))
		}

		val sub =
			SubscriptionImpl(
				subject = subject,
				queueGroup = queueGroup,
				sid = sid,
				scope = scope ?: clientScope,
				onStart = ::onStart,
				onStop = ::onStop,
				eagerSubscribe = eager,
				unsubscribeOnLastCollector = unsubscribeOnLastCollector,
				prefetchReplay = replayBuffer,
			)

		if (eager) {
			// suspend until the subscription becomes active
			sub.isActive
				.filter { active ->
					active
				}.first()
		}

		return sub
	}

	override suspend fun publish(
		subject: String,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: String?,
	) {
		val subject = Subject(subject)
		val replyTo = replyTo?.let { Subject(it) }

		publish(subject, message, headers, replyTo)
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

	override suspend fun publishBytes(byteMessageBlock: ByteMessageBuilder.() -> Unit) {
		val message = ByteMessageBuilder().apply { byteMessageBlock() }.build()
		publish(message)
	}

	override suspend fun publishString(stringMessageBlock: StringMessageBuilder.() -> Unit) {
		val message = StringMessageBuilder().apply { stringMessageBlock() }.build()
		publish(message)
	}

	override suspend fun request(
		subject: String,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		timeoutMs: Long,
		launchIn: CoroutineScope?,
	): Deferred<Message> {
		val inboxSubject = configuration.createInbox()
		val inbox = subscribe(inboxSubject, eager = true, replayBuffer = 1, unsubscribeOnLastCollector = false)
		val scope = launchIn ?: CoroutineScope(currentCoroutineContext())
		publish(Subject.from(subject), message, headers, replyTo = inboxSubject)
		val result = CompletableDeferred<Message>()
		scope.launch {
			try {
				withTimeout(timeoutMs) {
					result.complete(inbox.messages.first())
				}
			} catch (e: Exception) {
				logger.error { "request failed to $subject - ${e::class.simpleName}" }
				result.completeExceptionally(e)
			}
			inbox.unsubscribe()
		}
		return result
	}
}

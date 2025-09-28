@file:OptIn(InternalNatsApi::class)

package io.natskt.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import io.natskt.api.ConnectionPhase
import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.api.Subscription
import io.natskt.api.internal.InternalNatsApi
import io.natskt.api.internal.OnSubscriptionStart
import io.natskt.api.internal.OnSubscriptionStop
import io.natskt.api.validateSubject
import io.natskt.client.connection.ConnectionManagerImpl
import io.natskt.internal.ClientOperation
import io.natskt.internal.InternalSubscriptionHandler
import io.natskt.internal.RequestSubscriptionImpl
import io.natskt.internal.SubscriptionImpl
import io.natskt.internal.connectionCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
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
	private val clientScope = configuration.scope ?: CoroutineScope(SupervisorJob() + connectionCoroutineDispatcher + CoroutineName("NatsClient"))

	private val _subscriptions = ConcurrentMap<String, InternalSubscriptionHandler>()
	override val subscriptions: Map<String, Subscription>
		get() = _subscriptions

	internal val connectionManager = ConnectionManagerImpl(configuration, _subscriptions)

	@OptIn(ExperimentalAtomicApi::class)
	private val sidAllocator = AtomicInt(1)

	override suspend fun connect(): Result<Unit> {
		connectionManager.start()
		clientScope.launch {
			connectionManager.connectionStatus.collect {
				logger.debug { "Connection status change: $it" }
			}
		}

		withTimeoutOrNull(configuration.connectTimeoutMs) {
			connectionManager.connectionStatus
				.filter {
					it.phase == ConnectionPhase.Connected
				}.first()
		} ?: return Result.failure(Exception())

		return Result.success(Unit)
	}

	override suspend fun disconnect() {
		connectionManager.stop()
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

		val sub =
			SubscriptionImpl(
				subject = subject,
				queueGroup = queueGroup,
				sid = sid,
				scope = scope ?: clientScope,
				onStart = onSubscriptionStart,
				onStop = onSubscriptionStop,
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
		if (validateSubject(subject)) {
			throw IllegalArgumentException("invalid subject")
		}
		if (replyTo != null && validateSubject(replyTo)) {
			throw IllegalArgumentException("invalid reply-to")
		}

		publishUnchecked(subject, message, headers, replyTo)
	}

	override suspend fun publish(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: Subject?,
	) = publishUnchecked(subject.raw, message, headers, replyTo?.raw)

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

	private suspend fun publishUnchecked(
		subject: String,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: String?,
	) {
		val op =
			if (headers == null) {
				ClientOperation.PubOp(
					subject = subject,
					replyTo = replyTo,
					payload = message,
				)
			} else {
				ClientOperation.HPubOp(
					subject = subject,
					replyTo = replyTo,
					headers = headers,
					payload = message,
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

	@OptIn(ExperimentalAtomicApi::class)
	override suspend fun request(
		subject: String,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		timeoutMs: Long,
		launchIn: CoroutineScope?,
	): Deferred<Message> {
		val inboxSubject = configuration.createInbox()
		val sid = sidAllocator.fetchAndAdd(1).toString()
		val inbox =
			RequestSubscriptionImpl(
				sid = sid,
			)
		onSubscriptionStart(inbox, sid, inboxSubject, null)
		val scope = launchIn ?: clientScope
		publishUnchecked(subject, message, headers, replyTo = inboxSubject)
		scope.launch {
			try {
				withTimeout(timeoutMs) {
					inbox.response.await()
				}
			} catch (e: Exception) {
				logger.error { "request failed to $subject - ${e::class.simpleName}" }
				inbox.response.completeExceptionally(e)
			}
			onSubscriptionStop(sid, null)
		}
		return inbox.response
	}

	private val onSubscriptionStart =
		OnSubscriptionStart {
			sub: InternalSubscriptionHandler,
			sid: String,
			subject: String,
			queueGroup: String?,
			->

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

	private val onSubscriptionStop =
		OnSubscriptionStop {
			sid: String,
			maxMsgs: Int?,
			->
			_subscriptions[sid] ?: return@OnSubscriptionStop
			_subscriptions.remove(sid)
			connectionManager.send(ClientOperation.UnSubOp(sid, maxMsgs))
		}
}

@file:OptIn(InternalNatsApi::class)

package io.natskt.client

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import io.ktor.utils.io.ClosedWriteChannelException
import io.natskt.api.ConnectionPhase
import io.natskt.api.ConnectionState
import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.api.Subscription
import io.natskt.api.from
import io.natskt.api.internal.InternalNatsApi
import io.natskt.api.internal.OnSubscriptionStart
import io.natskt.api.internal.OnSubscriptionStop
import io.natskt.client.connection.ConnectionManagerImpl
import io.natskt.internal.ClientOperation
import io.natskt.internal.InternalSubscriptionHandler
import io.natskt.internal.PendingRequest
import io.natskt.internal.SubscriptionImpl
import io.natskt.internal.throwOnInvalidSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.Continuation
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.startCoroutine
import kotlin.time.Duration

private val logger = KotlinLogging.logger { }

internal class NatsClientImpl(
	val configuration: ClientConfiguration,
) : NatsClient {
	private val _subscriptions = ConcurrentMap<String, InternalSubscriptionHandler>()
	internal val pendingRequests = ConcurrentMap<String, PendingRequest>()
	private val requestLimiter = configuration.maxParallelRequests?.let { Semaphore(it.coerceAtLeast(1)) }
	override val subscriptions: Map<String, Subscription>
		get() = _subscriptions

	override val scope = configuration.scope

	internal val connectionManager = ConnectionManagerImpl(configuration, _subscriptions, pendingRequests)

	override val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

	@OptIn(ExperimentalAtomicApi::class)
	private val sidAllocator = AtomicLong(1)

	override suspend fun connect(): Result<Unit> {
		connectionManager.start()
		scope.launch {
			connectionManager.connectionState.collect {
				logger.debug { "Connection status change: $it" }
			}
		}

		withTimeoutOrNull(configuration.connectTimeoutMs) {
			connectionManager.connectionState
				.filter {
					it.phase == ConnectionPhase.Connected
				}.first()
		} ?: return Result.failure(Exception("timed out connecting to server"))

		return Result.success(Unit)
	}

	@OptIn(ExperimentalAtomicApi::class)
	override suspend fun subscribe(
		subject: String,
		queueGroup: String?,
		eager: Boolean,
		replayBuffer: Int,
		unsubscribeOnLastCollector: Boolean,
		scope: CoroutineScope?,
	): Subscription = subscribe(Subject.from(subject), queueGroup, eager, replayBuffer, unsubscribeOnLastCollector, scope)

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
				scope = scope ?: this.scope,
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
		message: ByteArray?,
		headers: Map<String, List<String>>?,
		replyTo: String?,
	) {
		subject.throwOnInvalidSubject()
		replyTo?.throwOnInvalidSubject()

		publishUnchecked(subject, message, headers, replyTo)
	}

	override suspend fun publish(
		subject: Subject,
		message: ByteArray?,
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
		message: ByteArray?,
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
		message: ByteArray?,
		headers: Map<String, List<String>>?,
		timeoutMs: Long,
	): Message {
		subject.throwOnInvalidSubject()
		val inboxSubject = configuration.createInbox()
		val sid = sidAllocator.fetchAndAdd(1).toString()
		var subscribed = false

		requestLimiter?.acquire()

		return try {
			withTimeout(timeoutMs) {
				suspendCancellableCoroutine { cont ->
					val pending = PendingRequest(cont)
					pendingRequests[sid] = pending

					cont.invokeOnCancellation {
						if (pendingRequests.remove(sid) != null) {
							suspend {
								connectionManager.send(ClientOperation.UnSubOp(sid, null))
							}.startCoroutine(
								object : Continuation<Unit> {
									override val context = cont.context

									override fun resumeWith(result: Result<Unit>) { }
								},
							)
						}
					}

					suspend {
						connectionManager.send(
							ClientOperation.SubOp(
								sid = sid,
								subject = inboxSubject,
								queueGroup = null,
							),
						)
						publishUnchecked(subject, message, headers, replyTo = inboxSubject)
					}.startCoroutine(
						object : Continuation<Unit> {
							override val context = cont.context

							override fun resumeWith(result: Result<Unit>) {
								if (result.isSuccess) {
									subscribed = true
								} else {
									val error = result.exceptionOrNull()
									if (error != null && pendingRequests.remove(sid) != null && cont.isActive) {
										cont.resumeWithException(error)
									}
								}
							}
						},
					)
				}
			}
		} finally {
			pendingRequests.remove(sid)
			if (subscribed) {
				try {
					connectionManager.send(ClientOperation.UnSubOp(sid, null))
				} catch (_: Throwable) {
					// ignore
				}
			}
			requestLimiter?.release()
		}
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
			try {
				connectionManager.send(ClientOperation.UnSubOp(sid, maxMsgs))
			} catch (_: ClosedWriteChannelException) {
				// already closed - ignore it
			}
		}

	override fun nextInbox(): String = configuration.createInbox()

	override suspend fun ping() = connectionManager.ping()

	override suspend fun flush() = connectionManager.flush()

	override suspend fun drain(timeout: Duration) = connectionManager.drain(timeout)

	override suspend fun disconnect() {
		connectionManager.stop()
		if (configuration.ownsScope) {
			configuration.scope.cancel()
		}
	}
}

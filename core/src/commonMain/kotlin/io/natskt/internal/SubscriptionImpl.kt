package io.natskt.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.natskt.api.Message
import io.natskt.api.Subscription
import io.natskt.api.internal.InternalSubscriptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

private val logger = KotlinLogging.logger { }

internal class SubscriptionImpl(
	override val subject: Subject,
	override val queueGroup: String?,
	override val sid: String,
	private val scope: CoroutineScope,
	private val onStart: suspend (sub: SubscriptionImpl, sid: String, subject: String, queueGroup: String?) -> Unit,
	private val onStop: suspend (sid: String, maxMsgs: Int?) -> Unit,
	/**
	 * Start listening for messages as soon as the subscription is constructed,
	 * instead of when the first collector on [messages] starts
	 */
	eagerSubscribe: Boolean = true,
	/**
	 * Unsubscribe when the last collector stops (debounced)
	 */
	private val unsubscribeOnLastCollector: Boolean,
	private val stopDebounceMillis: Long = 500L,
	prefetchReplay: Int,
	extraBufferCapacity: Int = 1024,
) : Subscription,
	InternalSubscriptionHandler {
	private val bus =
		MutableSharedFlow<Message>(
			replay = prefetchReplay,
			extraBufferCapacity = extraBufferCapacity,
			onBufferOverflow = BufferOverflow.DROP_OLDEST,
		)

	override val isActive = MutableStateFlow(false)

	@Volatile
	private var closed = false

	override val messages: Flow<Message> =
		channelFlow {
			val messageCollectorJob =
				launch {
					bus.collect { message -> send(message) }
				}

			val activeStateWatcherJob =
				launch {
					// suspend until the status becomes active for the first time
					isActive.filter { active -> active }.first()

					isActive.filter { active -> !active }.collect {
						channel.close()
					}
				}

			awaitClose {
				messageCollectorJob.cancel()
				activeStateWatcherJob.cancel()
			}
		}

	private val lifecycle = Mutex()
	private var stopJob: Job? = null
	private var interestWatchJob: Job? = null

	init {
		if (eagerSubscribe) {
			scope.launch { ensureStarted() }
		}

		interestWatchJob =
			scope.launch {
				var initialised = false
				bus.subscriptionCount.collect { count ->
					if (count > 0) {
						cancelStopIfAny()
						ensureStarted()
						initialised = true
					} else if (initialised && unsubscribeOnLastCollector) {
						scheduleStop()
					}
				}
			}
	}

	override suspend fun emit(msg: Message) = bus.emit(msg)

	private suspend fun ensureStarted() =
		lifecycle.withLock {
			if (closed || isActive.value) return
			onStart(this, sid, subject.raw, queueGroup)
			isActive.emit(true)
		}

	private suspend fun ensureStopped() =
		lifecycle.withLock {
			logger.trace { "Dropping subscription $sid" }
			if (!isActive.value) return
			interestWatchJob?.cancel()
			interestWatchJob = null
			onStop(sid, null)
			isActive.emit(false)
		}

	private fun cancelStopIfAny() {
		stopJob?.cancel()
		stopJob = null
	}

	private fun scheduleStop() {
		if (closed) return
		cancelStopIfAny()
		stopJob =
			scope.launch {
				delay(stopDebounceMillis)
				if (bus.subscriptionCount.value == 0) {
					ensureStopped()
				}
			}
	}

	override suspend fun unsubscribe() {
		closed = true
		cancelStopIfAny()
		ensureStopped()
	}

	override fun close() { }
}

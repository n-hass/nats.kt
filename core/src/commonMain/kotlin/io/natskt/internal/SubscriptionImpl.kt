package io.natskt.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.natskt.api.Message
import io.natskt.api.Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
	 * Start the subscription as soon as the subscription is constructed
	 */
	eagerSubscribe: Boolean = true,
	/**
	 * Unsubscribe when the last collector stops (debounced)
	 */
	private val autoUnsubscribeOnLastCollector: Boolean = true,
	private val stopDebounceMillis: Long = 500L,
	prefetchReplay: Int = 32,
	extraBufferCapacity: Int = 1024,
) : Subscription {
	private val bus =
		MutableSharedFlow<Message>(
			replay = prefetchReplay,
			extraBufferCapacity = extraBufferCapacity,
			onBufferOverflow = BufferOverflow.DROP_OLDEST,
		)

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

	override val isActive = MutableStateFlow(false)

	private val lifecycle = Mutex()
	private var stopJob: Job? = null

	@Volatile
	private var closed = false

	init {
		// If eager, start immediately. This is the only place we will call
		// ensureStarted proactively.
		if (eagerSubscribe) {
			scope.launch { ensureStarted() }
		}

		scope.launch {
			try {
				bus.subscriptionCount.collect { count ->
					// The logic is now unified. The number of subscribers
					// is the single source of truth for the subscription's state.
					if (count > 0) {
						// A collector is present, cancel any pending stop and ensure we are started.
						cancelStopIfAny()
						ensureStarted()
					} else if (autoUnsubscribeOnLastCollector) {
						scheduleStop()
					}
				}
			} finally {
				with(NonCancellable) { runCatching { unsubscribe() } }
			}
		}
	}

	suspend fun emit(msg: Message) = bus.emit(msg)

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
}

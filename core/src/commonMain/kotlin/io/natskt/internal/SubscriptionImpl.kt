package io.natskt.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.natskt.api.Message
import io.natskt.api.Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
	private val onStart: suspend () -> Unit,
	private val onStop: suspend () -> Unit,
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

	override val messages: Flow<Message> = bus
	override val isActive = MutableStateFlow(false)

	private val lifecycle = Mutex()
	private var stopJob: Job? = null

	@Volatile
	private var closed = false

	init {
		if (eagerSubscribe) {
			scope.launch { ensureStarted() }
		}

		scope.launch {
			try {
				bus.subscriptionCount
					.collect { count ->
						if (closed) return@collect

						if (count > 0 && !isActive.value) {
							// If this isn't eager, then start on collection
							ensureStarted()
						}

						if (autoUnsubscribeOnLastCollector) {
							if (count == 0) {
								scheduleStop()
							} else {
								cancelStopIfAny()
							}
						}
					}
			} finally {
				with(NonCancellable) {
					runCatching { unsubscribe() }
				}
			}
		}
	}

	suspend fun emit(msg: Message) = bus.emit(msg)

	private suspend fun ensureStarted() =
		lifecycle.withLock {
			if (closed || isActive.value) return
			onStart()
			isActive.value = true
		}

	private suspend fun ensureStopped() =
		lifecycle.withLock {
			logger.trace { "Cleaning subscription $sid" }
			if (!isActive.value) return
			onStop()
			isActive.value = false
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

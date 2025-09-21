package io.natskt.internal

import io.natskt.api.Message
import io.natskt.api.Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

internal class SubscriptionImpl(
	override val subject: Subject,
	override val queueGroup: String?,
	override val sid: String,
	private val scope: CoroutineScope,
	private val onStart: suspend (sid: String, handler: (Message) -> Unit) -> Unit,
	private val onStop: suspend (sid: String) -> Unit,
	prefetchReplay: Int = 32,
	extraBufferCapacity: Int = 1024,
	private val stopDebounceMillis: Long = 500L,
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
		scope.launch { ensureStarted() }

		scope.launch {
			bus.subscriptionCount.collect { count ->
				if (closed) return@collect
				if (count == 0) scheduleStop() else cancelStopIfAny()
			}
		}

		scope.coroutineContext[Job]?.invokeOnCompletion {
			scope.launch { runCatching { unsubscribe() } }
		}
	}

	fun tryEmit(msg: Message) {
		bus.tryEmit(msg)
	}

	private suspend fun ensureStarted() =
		lifecycle.withLock {
			if (closed || isActive.value) return
			onStart(sid) { msg -> tryEmit(msg) }
			isActive.value = true
		}

	private suspend fun ensureStopped() =
		lifecycle.withLock {
			if (!isActive.value) return
			onStop(sid)
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
				if (bus.subscriptionCount.value == 0) ensureStopped()
			}
	}

	override suspend fun unsubscribe() {
		closed = true
		cancelStopIfAny()
		ensureStopped()
	}
}

package io.natskt.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * https://kt.academy/article/s_suspended_lazy
 */
fun <T> suspendLazy(initializer: suspend () -> T): SuspendLazy<T> {
	var innerInitializer: (suspend () -> T)? = initializer
	val mutex = Mutex()
	var holder: Any? = Any()

	return object : SuspendLazy<T> {
		override val isInitialized: Boolean
			get() = innerInitializer == null

		@Suppress("UNCHECKED_CAST")
		override fun valueOrNull(): T? = if (isInitialized) holder as T else null

		@Suppress("UNCHECKED_CAST")
		override suspend fun invoke(): T =
			if (isInitialized) {
				holder as T
			} else {
				mutex.withLock {
					innerInitializer?.let {
						holder = it()
						innerInitializer = null
					}
					holder as T
				}
			}
	}
}

interface SuspendLazy<T> : suspend () -> T {
	val isInitialized: Boolean

	fun valueOrNull(): T?

	override suspend operator fun invoke(): T
}

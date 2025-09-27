package io.natskt.internal

import io.natskt.api.Message
import io.natskt.api.internal.InternalSubscriptionHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal class RequestSubscriptionImpl(
	override val sid: String,
) : InternalSubscriptionHandler {
	val response = CompletableDeferred<Message>()

	@Suppress("CAST_NEVER_SUCCEEDS")
	override suspend fun emit(msg: Message): Unit = response.complete(msg) as Unit

	override val subject: Subject
		get() = throw UnsupportedOperationException()

	override val queueGroup: String?
		get() = null

	override val isActive: StateFlow<Boolean>
		get() = throw UnsupportedOperationException()

	override val messages: Flow<Message>
		get() = throw UnsupportedOperationException()

	override suspend fun unsubscribe() { }

	override fun close() { }
}

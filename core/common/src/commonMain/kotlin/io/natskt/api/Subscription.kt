package io.natskt.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public interface Subscription : AutoCloseable {
	public val subject: Subject
	public val queueGroup: String?
	public val sid: String
	public val isActive: StateFlow<Boolean>

	/**
	 * Collect messages from the subscription.
	 *
	 * Will auto-unsubscribe once nothing is collecting
	 */
	public val messages: Flow<Message>

	public suspend fun unsubscribe()

	/**
	 * initiates [unsubscribe]
	 */
	abstract override fun close()
}

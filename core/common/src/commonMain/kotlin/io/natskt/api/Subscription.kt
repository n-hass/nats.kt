package io.natskt.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

public interface Subscription : AutoCloseable {
	public val subject: Subject
	public val queueGroup: String?
	public val sid: String
	public val isActive: StateFlow<Boolean> // true while we have a SUB on the wire

	/**
	 * Collect messages from the subscription.
	 *
	 * Will auto-unsubscribe once nothing is collecting
	 */
	public val messages: Flow<Message> // collect this; stops → auto UNSUB

	public suspend fun unsubscribe()

	/**
	 * initiates [unsubscribe]
	 */
	abstract override fun close()
}

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
	 * Auto-unsubscribe after [after] more messages have been delivered.
	 *
	 * Sends `UNSUB <sid> <after>` to the server, which delivers up to [after] more
	 * messages on this subscription and then auto-unsubscribes server-side.
	 * Once [after] messages have been received, the subscription's [messages]
	 * flow completes and [isActive] flips to `false`.
	 *
	 * @throws IllegalArgumentException if [after] is not positive
	 */
	public suspend fun unsubscribe(after: Int)

	/**
	 * initiates [unsubscribe]
	 */
	abstract override fun close()
}

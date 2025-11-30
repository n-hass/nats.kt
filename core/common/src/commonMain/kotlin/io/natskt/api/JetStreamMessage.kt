package io.natskt.api

import kotlin.time.Instant

public interface JetStreamMessage : Message {
	/**
	 * Acknowledge a message
	 */
	public suspend fun ack()

	/**
	 * 'Double-Acknowledge' - wait for the server to acknowledge your message ack
	 */
	public suspend fun ackSync()

	public suspend fun nak()

	public suspend fun inProgress()

	public suspend fun term()

	public val metadata: Metadata?

	public data class Metadata(
		val streamSequence: ULong,
		val pending: ULong,
		val timestamp: Instant,
	)
}

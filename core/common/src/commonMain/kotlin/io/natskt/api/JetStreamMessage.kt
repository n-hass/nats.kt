package io.natskt.api

import kotlin.time.Duration
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

	/**
	 * 'Double-Acknowledge' with an explicit caller-bound timeout.
	 *
	 * Throws on timeout (transport-specific exception type) instead of waiting for the
	 * default request timeout.
	 */
	public suspend fun ackSync(timeout: Duration)

	public suspend fun nak()

	/**
	 * Negative-acknowledge with a server-honored redelivery delay. Replaces the next-redelivery
	 * scheduling for this message only; subsequent redeliveries still follow `ack_wait` / `backoff`.
	 *
	 * Requires NATS server ≥ 2.7.
	 */
	public suspend fun nakWithDelay(delay: Duration)

	public suspend fun inProgress()

	public suspend fun term()

	/**
	 * Terminate with a human-readable reason. The server includes the reason in the
	 * advisory event payload.
	 *
	 * Requires NATS server ≥ 2.10.4. Older servers ignore the reason silently.
	 */
	public suspend fun term(reason: String)

	public val metadata: Metadata?

	public data class Metadata(
		val streamSequence: ULong,
		val pending: ULong,
		val timestamp: Instant,
	)
}

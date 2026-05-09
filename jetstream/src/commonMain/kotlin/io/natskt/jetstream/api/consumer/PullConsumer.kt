package io.natskt.jetstream.api.consumer

import io.natskt.api.JetStreamMessage
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public interface PullConsumer : Consumer {
	/**
	 * Issue a single pull request and collect up to [batch] messages.
	 *
	 * @param group priority-group filter. Pair with `priorityPolicy`/`priorityGroups`
	 *   on the consumer config; the server gates delivery to clients matching the
	 *   group.
	 * @param minPending the server only delivers if the consumer has at least this
	 *   many messages pending (priority-overflow primitive).
	 * @param minAckPending the server only delivers if at least this many messages
	 *   are pending-ack on the consumer (priority-overflow primitive).
	 */
	public suspend fun fetch(
		batch: Int,
		expires: Duration? = null,
		noWait: Boolean? = null,
		maxBytes: Int? = null,
		heartbeat: Duration? = null,
		group: String? = null,
		minPending: Long? = null,
		minAckPending: Long? = null,
	): List<JetStreamMessage>

	/**
	 * Continuously consume from this pull consumer, returning a hot [Flow] that
	 * issues pull requests as the in-flight buffer drains and refills when below
	 * `options.thresholdMessages`.
	 *
	 * Status surfacing differs from [fetch]: `409 BatchCompleted`,
	 * `409 MaxBytesExceeded`, `409 LeadershipChange`, `423 Pin ID Mismatch` are
	 * recoverable inside the consume loop and trigger a refill rather than
	 * terminating the Flow. `409 ConsumerDeleted` / `400 BadRequest` /
	 * `503 NoResponders` and unknown statuses terminate the Flow with a typed
	 * exception.
	 *
	 * Cancelling the collector cancels the underlying pull loop. Pending pulls
	 * expire naturally on the server via their `expires` window.
	 */
	public fun consume(options: ConsumeOptions = ConsumeOptions()): Flow<JetStreamMessage>
}

/**
 * Configuration for a continuous [PullConsumer.consume].
 *
 * @param batch maximum number of messages to keep buffered across in-flight pulls.
 * @param maxBytes optional byte-budget per pull request.
 * @param expires per-pull expiry window.
 * @param thresholdMessages refill the buffer when remaining drops below this.
 *   Defaults to `ceil(batch / 2)` when null.
 * @param idleHeartbeat per-pull idle heartbeat cadence. Defaults to
 *   `min(expires / 2, 30.seconds)` when null. Must be `<= expires / 2`.
 * @param group priority-group filter; required when consumer has `priorityGroups` set.
 * @param minPending overflow-priority gate: only deliver if at least this many pending.
 * @param minAckPending overflow-priority gate: only deliver if at least this many pending-ack.
 */
public data class ConsumeOptions(
	val batch: Int = 500,
	val maxBytes: Int? = null,
	val expires: Duration = 30.seconds,
	val thresholdMessages: Int? = null,
	val idleHeartbeat: Duration? = null,
	val group: String? = null,
	val minPending: Long? = null,
	val minAckPending: Long? = null,
)

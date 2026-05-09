package io.natskt.jetstream.api.consumer

import io.natskt.api.JetStreamMessage
import kotlin.time.Duration

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
}

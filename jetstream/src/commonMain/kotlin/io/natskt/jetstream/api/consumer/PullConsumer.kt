package io.natskt.jetstream.api.consumer

import io.natskt.api.JetStreamMessage
import kotlin.time.Duration

public interface PullConsumer : Consumer {
	public suspend fun fetch(
		batch: Int,
		expires: Duration? = null,
		noWait: Boolean? = null,
		maxBytes: Int? = null,
		heartbeat: Duration? = null,
	): List<JetStreamMessage>
}

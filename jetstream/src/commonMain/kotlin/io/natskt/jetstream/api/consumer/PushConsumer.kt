package io.natskt.jetstream.api.consumer

import io.natskt.api.JetStreamMessage
import kotlinx.coroutines.flow.Flow

public interface PushConsumer : Consumer {
	public val messages: Flow<JetStreamMessage>
}

public class JetStreamHeartbeatException(
	msg: String,
) : Exception(msg)

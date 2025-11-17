package io.natskt.jetstream.api.consumer

import io.natskt.api.Message
import kotlinx.coroutines.flow.Flow

public interface PushConsumer : Consumer {
	public val messages: Flow<Message>
}

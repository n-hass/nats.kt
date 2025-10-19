package io.natskt.jetstream.api.consumer

import io.natskt.api.JetStreamMessage
import io.natskt.jetstream.api.JetStreamMessageMetadata

public interface PullMessage : JetStreamMessage {
	public val metadata: JetStreamMessageMetadata
}

package io.natskt.jetstream.api

import io.natskt.api.JetStreamMessage

public interface PullMessage : JetStreamMessage {
	public val metadata: JetStreamMessageMetadata
}

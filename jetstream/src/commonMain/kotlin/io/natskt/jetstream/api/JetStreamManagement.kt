package io.natskt.jetstream.api

import io.natskt.jetstream.api.stream.Stream
import io.natskt.jetstream.api.stream.StreamConfigurationBuilder

public interface JetStreamManagement {
	/**
	 * Create a new stream
	 */
	public suspend fun createStream(configure: StreamConfigurationBuilder.() -> Unit): Stream
}

package io.natskt.jetstream.api

import io.natskt.api.NatsClient
import io.natskt.jetstream.client.JetStreamClientImpl
import io.natskt.jetstream.client.JetStreamConfiguration

public interface JetStreamClient {
	public companion object {
		internal operator fun invoke(
			client: NatsClient,
			config: JetStreamConfiguration,
		) = JetStreamClientImpl(client, config)
	}
}

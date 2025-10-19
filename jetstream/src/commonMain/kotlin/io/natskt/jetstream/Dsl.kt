package io.natskt.jetstream

import io.natskt.api.NatsClient
import io.natskt.api.internal.InternalNatsApi
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.client.JetStreamConfigurationBuilder
import io.natskt.jetstream.client.build

@OptIn(InternalNatsApi::class)
public suspend fun JetStreamClient(
	client: NatsClient,
	block: JetStreamConfigurationBuilder.() -> Unit = {},
): JetStreamClient {
	val config = JetStreamConfigurationBuilder().apply(block).build()
	val inbox = client.subscribe(client.nextInbox() + ".*", replayBuffer = 0, unsubscribeOnLastCollector = false, eager = true)
	return JetStreamClient(client, config, inbox)
}

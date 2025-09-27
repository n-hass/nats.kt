package io.natskt.jetstream.client

import io.natskt.api.NatsClient
import io.natskt.jetstream.api.JetStreamClient

internal class JetStreamClientImpl(
	private val client: NatsClient,
	private val config: JetStreamConfiguration,
) : JetStreamClient

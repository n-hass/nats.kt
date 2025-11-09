package io.natskt.jetstream.internal

import io.natskt.jetstream.client.JetStreamConfiguration

public data class JetStreamContext(
	val config: JetStreamConfiguration,
	val directGet: Boolean = true,
)

package io.natskt.jetstream.api

import io.natskt.jetstream.api.kv.KeyValueConfig

public class KeyValueBucket internal constructor(
	public val status: KeyValueStatus,
	public val config: KeyValueConfig,
)

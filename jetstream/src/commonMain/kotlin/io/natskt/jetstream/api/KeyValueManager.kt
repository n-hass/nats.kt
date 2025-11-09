package io.natskt.jetstream.api

import io.natskt.jetstream.api.kv.KeyValueBucket
import io.natskt.jetstream.api.kv.KeyValueConfigurationBuilder

public interface KeyValueManager {
	public suspend fun get(bucket: String): KeyValueBucket

	public suspend fun create(configure: KeyValueConfigurationBuilder.() -> Unit): KeyValueBucket
}

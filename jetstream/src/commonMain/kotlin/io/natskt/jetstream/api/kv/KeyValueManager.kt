package io.natskt.jetstream.api.kv

public interface KeyValueManager {
	public suspend fun get(bucket: String): KeyValueBucket

	public suspend fun create(configure: KeyValueConfigurationBuilder.() -> Unit): KeyValueBucket
}

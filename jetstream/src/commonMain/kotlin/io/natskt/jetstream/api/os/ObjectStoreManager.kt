package io.natskt.jetstream.api.os

/**
 * Lifecycle entry point for Object Store buckets. Mirrors `KeyValueManager`.
 */
public interface ObjectStoreManager {
	public suspend fun get(bucket: String): ObjectStoreBucket

	public suspend fun create(configure: ObjectStoreConfigurationBuilder.() -> Unit): ObjectStoreBucket
}

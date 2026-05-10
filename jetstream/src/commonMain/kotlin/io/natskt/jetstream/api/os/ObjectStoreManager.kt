package io.natskt.jetstream.api.os

/**
 * Lifecycle entry point for Object Store buckets. Mirrors `KeyValueManager`
 * with the additional `update`, `delete`, `names`, and `list` operations from
 * `ObjectStoreManagement` in nats.java / nats.go.
 */
public interface ObjectStoreManager {
	/** Bind to an existing bucket. Fails if the bucket does not exist. */
	public suspend fun get(bucket: String): ObjectStoreBucket

	/** Create a new bucket. Fails if it already exists. */
	public suspend fun create(configure: ObjectStoreConfigurationBuilder.() -> Unit): ObjectStoreBucket

	/**
	 * Update the mutable config of an existing bucket (description, maxBytes,
	 * ttl, replicas, placement, compression, metadata). Fails if the bucket
	 * does not exist.
	 */
	public suspend fun update(
		bucket: String,
		configure: ObjectStoreConfigurationBuilder.() -> Unit,
	): ObjectStoreBucket

	/**
	 * Delete a bucket and all of its contents. Returns true on success.
	 * Fails if the bucket does not exist.
	 */
	public suspend fun delete(bucket: String): Boolean

	/** Names of every Object Store bucket known to this server. */
	public suspend fun names(): List<String>

	/** Snapshot status for every Object Store bucket known to this server. */
	public suspend fun list(): List<ObjectStoreStatus>
}

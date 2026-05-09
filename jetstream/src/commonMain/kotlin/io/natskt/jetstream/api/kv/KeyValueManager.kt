package io.natskt.jetstream.api.kv

import io.natskt.jetstream.api.KeyValueStatus

public interface KeyValueManager {
	/**
	 * Bind to an existing bucket and return a [KeyValueBucket] handle.
	 */
	public suspend fun get(bucket: String): KeyValueBucket

	/**
	 * Create a new bucket. Fails if the underlying `KV_<bucket>` stream already exists.
	 */
	public suspend fun create(configure: KeyValueConfigurationBuilder.() -> Unit): KeyValueBucket

	/**
	 * Update the configuration of an existing bucket. The bucket name is required in the
	 * builder block.
	 */
	public suspend fun update(configure: KeyValueConfigurationBuilder.() -> Unit): KeyValueStatus

	/**
	 * Delete the bucket and its underlying stream.
	 *
	 * @return true if the delete succeeded.
	 */
	public suspend fun delete(bucket: String): Boolean

	/**
	 * Get the current status of a bucket without binding to it.
	 */
	public suspend fun getStatus(bucket: String): KeyValueStatus

	/**
	 * Get the status of every KV bucket visible to this account.
	 */
	public suspend fun getStatuses(): List<KeyValueStatus>

	/**
	 * Enumerate the names of every KV bucket visible to this account, with the
	 * `KV_` prefix stripped.
	 */
	public suspend fun getBucketNames(): List<String>
}

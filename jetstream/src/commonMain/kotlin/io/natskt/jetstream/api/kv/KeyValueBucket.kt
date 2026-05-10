package io.natskt.jetstream.api.kv

import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.KeyValueStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * A handle to a Key-Value bucket. Use this to perform client operations against a bucket.
 *
 * Implementations hold a persistent request subscription, so callers must call [close] when
 * finished — or use [AutoCloseable.use] to scope the lifetime.
 */
public interface KeyValueBucket : AutoCloseable {
	/** The bucket's name */
	public val name: String

	/**
	 * Reflects the most recently fetched [KeyValueStatus]. Starts at the value captured when the
	 * bucket handle was constructed (or `null` for a freshly bound bucket) and is updated by
	 * [updateBucketStatus] — collectors observe each refresh.
	 */
	public val status: StateFlow<KeyValueStatus?>

	/** The most recently fetched [KeyValueConfig], or null if it has not been fetched yet. */
	public val config: StateFlow<KeyValueConfig?>

	/**
	 * Refreshes the cached bucket status and configuration by querying JetStream for the latest stream info.
	 *
	 * @return a [Result] containing the latest [KeyValueStatus] or the error that occurred.
	 */
	public suspend fun updateBucketStatus(): Result<KeyValueStatus>

	/**
	 * Writes the provided [value] under [key], creating or replacing the latest revision without any precondition.
	 *
	 * @return the JetStream sequence associated with the stored revision.
	 */
	public suspend fun put(
		key: String,
		value: ByteArray,
	): ULong

	/**
	 * Creates a new entry for [key] only if no existing entry is there (or it has been deleted/purged and not replaced since).
	 *
	 * This mirrors the server-side `create` semantics by allowing recreation after a delete/purge.
	 *
	 * When [ttl] is set, the entry carries a `Nats-TTL` header so the server expires it after the
	 * given duration. The bucket must have been created with [KeyValueConfig.limitMarkerTtl]
	 * (i.e. `allow_msg_ttl=true`) for the TTL to take effect; otherwise the server silently ignores it.
	 *
	 * @throws JetStreamApiException when the key is taken
	 */
	public suspend fun create(
		key: String,
		value: ByteArray,
		ttl: Duration? = null,
	): ULong

	/**
	 * Updates [key] with [value], asserting that the previous revision equals [lastRevision].
	 *
	 * @throws JetStreamApiException if the expected revision does not match or publishing fails.
	 */
	public suspend fun update(
		key: String,
		value: ByteArray,
		lastRevision: ULong,
		ttl: Duration? = null,
	): ULong

	/**
	 * Appends a delete marker for [key], optionally guarding on [lastRevision] for optimistic concurrency.
	 *
	 * @return the sequence of the delete revision.
	 */
	public suspend fun delete(
		key: String,
		lastRevision: ULong? = null,
	): ULong

	/**
	 * Permanently purges the value and history for [key], optionally verifying [lastRevision] first.
	 *
	 * When [ttl] is set, the purge tombstone carries a `Nats-TTL` header so the marker itself ages
	 * out after the given duration (requires `allow_msg_ttl=true` on the bucket).
	 *
	 * @return the sequence for the purge operation.
	 */
	public suspend fun purge(
		key: String,
		lastRevision: ULong? = null,
		ttl: Duration? = null,
	): ULong

	/**
	 * Retrieves the latest or specified [revision] for [key] and converts it to a [KeyValueEntry].
	 *
	 * @throws JetStreamApiException when JetStream rejects the lookup.
	 */
	public suspend fun get(
		key: String,
		revision: ULong? = null,
	): KeyValueEntry

	/**
	 * Watches revisions for [key], emitting a [Flow] of entries including future updates.
	 *
	 * The flow is unbounded — it emits the initial snapshot followed by every subsequent update,
	 * and stays open until the collector cancels.
	 */
	public suspend fun watch(
		key: String,
		config: KeyValueWatchConfig = KeyValueWatchConfig.Default,
	): Flow<KeyValueEntry>

	/**
	 * Watches revisions for any key in [keys] on a single underlying consumer.
	 *
	 * Multi-filter watches require `nats-server` 2.10+; on older servers the create-consumer call
	 * fails with a typed [JetStreamApiException].
	 */
	public suspend fun watch(
		keys: List<String>,
		config: KeyValueWatchConfig = KeyValueWatchConfig.Default,
	): Flow<KeyValueEntry>

	/**
	 * Watches every key in the bucket. Equivalent to `watch(">", config)`.
	 */
	public suspend fun watchAll(config: KeyValueWatchConfig = KeyValueWatchConfig.Default): Flow<KeyValueEntry>

	/**
	 * Returns every retained revision for [key], oldest-first, up to the bucket's history limit.
	 */
	public suspend fun history(key: String): List<KeyValueEntry>

	/**
	 * Lists the keys currently stored in the bucket, optionally constrained by a key [filter].
	 *
	 * Materializes the full list; for very large buckets prefer [consumeKeys] to stream keys
	 * without buffering.
	 */
	public suspend fun keys(filter: String? = null): List<String>

	/**
	 * Lists the keys whose subject matches any of [filters]. Requires `nats-server` 2.10+.
	 */
	public suspend fun keys(filters: List<String>): List<String>

	/**
	 * Streams the keys currently stored in the bucket, optionally constrained by [filter].
	 *
	 * The flow completes once the initial snapshot finishes, so callers can use it in a normal
	 * `collect { }` loop. Each emitted [String] is a key (not a fully-qualified subject).
	 */
	public suspend fun consumeKeys(filter: String? = null): Flow<String>

	/**
	 * Streams the keys whose subject matches any of [filters]. Requires `nats-server` 2.10+.
	 *
	 * Tombstone entries (delete and purge markers) are filtered out, so the emitted strings
	 * represent live keys only.
	 */
	public suspend fun consumeKeys(filters: List<String>): Flow<String>

	/**
	 * Removes delete and purge tombstones from the underlying stream.
	 *
	 * Markers older than [KeyValuePurgeOptions.deleteMarkersThreshold] (or all of them when
	 * [KeyValuePurgeOptions.noThreshold] is `true`) are removed entirely; markers within the
	 * threshold window have their preceding history truncated but the marker itself is kept so
	 * watchers can still observe the deletion.
	 */
	public suspend fun purgeDeletes(options: KeyValuePurgeOptions = KeyValuePurgeOptions.Default)

	/** Releases any persistent request subscription associated with this bucket instance. */
	override fun close()
}

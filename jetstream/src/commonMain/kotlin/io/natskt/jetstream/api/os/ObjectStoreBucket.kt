package io.natskt.jetstream.api.os

import kotlinx.coroutines.flow.Flow

/**
 * A handle to an Object Store bucket: a JetStream-backed container of arbitrarily-sized binary
 * objects.
 *
 * Implementations hold a persistent inbox subscription used by direct-get and stream-info
 * requests, so callers must call [close] when finished — or wrap the lifetime in
 * [AutoCloseable.use].
 */
public interface ObjectStoreBucket : AutoCloseable {
	/** The bucket name (without the `OBJ_` stream prefix). */
	public val name: String

	/** The most recently fetched [ObjectStoreStatus], or null if it has not been fetched yet. */
	public val status: ObjectStoreStatus?

	/** The most recently fetched [ObjectStoreConfig], or null if it has not been fetched yet. */
	public val config: ObjectStoreConfig?

	/**
	 * Refreshes the cached [status] and [config] by querying JetStream for the latest backing
	 * stream info.
	 */
	public suspend fun updateBucketStatus(): Result<ObjectStoreStatus>

	/**
	 * Stores [value] under a fresh [ObjectMeta] with the given object [name]. Replaces any prior
	 * object with the same name, atomically swapping the meta record once all chunks land.
	 */
	public suspend fun put(
		name: String,
		value: ByteArray,
	): ObjectInfo

	/** Stores [value] under [meta]. Replaces any prior object with the same `meta.name`. */
	public suspend fun put(
		meta: ObjectMeta,
		value: ByteArray,
	): ObjectInfo

	/**
	 * Streams [source] into a new object identified by [meta]. The flow is consumed once and
	 * chunks are published as they arrive; the meta record is published once the stream completes.
	 */
	public suspend fun put(
		meta: ObjectMeta,
		source: Flow<ByteArray>,
	): ObjectInfo

	/**
	 * Reads the latest revision of [name] in full. Resolves object links transparently and
	 * verifies size and digest against the stored meta.
	 *
	 * @throws ObjectNotFound if no live object with that name exists.
	 * @throws GetSizeMismatch / GetDigestMismatch if the payload is corrupted.
	 */
	public suspend fun get(name: String): GetResult

	/**
	 * Returns a streaming view of [name] — the [ObjectInfo] is resolved up front and chunks are
	 * exposed as a `Flow<ByteArray>` for consumption without buffering the full payload.
	 */
	public suspend fun getStream(name: String): ObjectStreamResult

	/**
	 * Returns the metadata for [name], or `null` if no such object exists.
	 *
	 * @param includingDeleted when `true`, tombstones are returned with `deleted = true`. When
	 * `false`, deleted objects are reported as missing.
	 */
	public suspend fun getInfo(
		name: String,
		includingDeleted: Boolean = false,
	): ObjectInfo?

	/** Lists every live (non-deleted) object's [ObjectInfo] in this bucket. */
	public suspend fun getList(): List<ObjectInfo>

	/**
	 * Marks [name] as deleted and purges its chunks. Returns the tombstone [ObjectInfo].
	 *
	 * @throws ObjectNotFound if no object with that name has ever been stored.
	 */
	public suspend fun delete(name: String): ObjectInfo

	/**
	 * Watches the bucket for object changes. The flow emits `null` once after the initial
	 * snapshot completes (unless [ObjectStoreWatchOption.UpdatesOnly] is set, which skips the
	 * snapshot entirely), then continues with live updates until the collector cancels.
	 */
	public suspend fun watch(options: Set<ObjectStoreWatchOption> = emptySet()): Flow<ObjectInfo?>

	/**
	 * Updates the metadata for an existing object. May rename the object via [newMeta]`.name`,
	 * which purges the prior name's chunks.
	 *
	 * @throws ObjectNotFound when the object does not exist.
	 * @throws ObjectIsDeleted when the object is a tombstone.
	 * @throws ObjectAlreadyExists when renaming would collide with an existing object.
	 */
	public suspend fun updateMeta(
		name: String,
		newMeta: ObjectMeta,
	): ObjectInfo

	/**
	 * Adds a link from [objectName] in this bucket to [target] in (potentially) another bucket.
	 *
	 * @throws ObjectIsDeleted if [target] is a tombstone.
	 * @throws CantLinkToLink if [target] is itself a link.
	 * @throws ObjectAlreadyExists if [objectName] already names a non-link object.
	 */
	public suspend fun addLink(
		objectName: String,
		target: ObjectInfo,
	): ObjectInfo

	/**
	 * Adds a bucket-link from [objectName] in this bucket to the [target] bucket as a whole.
	 *
	 * @throws ObjectAlreadyExists if [objectName] already names a non-link object.
	 */
	public suspend fun addBucketLink(
		objectName: String,
		target: ObjectStoreBucket,
	): ObjectInfo

	/** Seals the underlying stream so future writes are rejected. */
	public suspend fun seal(): ObjectStoreStatus

	/** Releases any persistent inbox subscription associated with this bucket instance. */
	override fun close()
}

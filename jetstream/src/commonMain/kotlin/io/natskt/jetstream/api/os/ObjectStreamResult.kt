package io.natskt.jetstream.api.os

import kotlinx.coroutines.flow.Flow

/**
 * The result of [ObjectStoreBucket.getStream]: the resolved [ObjectInfo]
 * plus a chunk [Flow] that lazily streams the object's bytes from JetStream.
 *
 * The flow performs digest, size, and chunk-count validation on completion;
 * mismatches surface as [GetChunksMismatch], [GetSizeMismatch], or
 * [GetDigestMismatch] thrown into the collector.
 */
public data class ObjectStreamResult(
	public val info: ObjectInfo,
	public val data: Flow<ByteArray>,
)

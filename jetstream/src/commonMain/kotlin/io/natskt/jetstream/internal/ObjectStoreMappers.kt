package io.natskt.jetstream.internal

import io.natskt.jetstream.api.DiscardPolicy
import io.natskt.jetstream.api.StreamCompression
import io.natskt.jetstream.api.StreamConfig
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.os.ObjectStoreConfig
import io.natskt.jetstream.api.os.ObjectStoreStatus
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal const val OBJ_BUCKET_STREAM_NAME_PREFIX: String = "OBJ_"
internal const val OBJ_SUBJECT_PREFIX: String = "\$O."
internal const val OBJ_META_PART: String = ".M"
internal const val OBJ_CHUNK_PART: String = ".C"
internal const val OBJ_SUBJECT_SUFFIX: String = ".>"

internal const val OBJ_DEFAULT_CHUNK_SIZE: Int = 128 * 1024

internal fun toObjectStoreStreamName(bucket: String): String = OBJ_BUCKET_STREAM_NAME_PREFIX + bucket

internal fun toObjectStoreMetaStreamSubject(bucket: String): String = OBJ_SUBJECT_PREFIX + bucket + OBJ_META_PART + OBJ_SUBJECT_SUFFIX

internal fun toObjectStoreChunkStreamSubject(bucket: String): String = OBJ_SUBJECT_PREFIX + bucket + OBJ_CHUNK_PART + OBJ_SUBJECT_SUFFIX

internal fun toObjectStoreMetaPrefix(bucket: String): String = OBJ_SUBJECT_PREFIX + bucket + OBJ_META_PART + "."

internal fun toObjectStoreChunkPrefix(bucket: String): String = OBJ_SUBJECT_PREFIX + bucket + OBJ_CHUNK_PART + "."

internal fun toObjectStoreAllMetaSubject(bucket: String): String = toObjectStoreMetaPrefix(bucket) + ">"

internal fun chunkSubjectFor(
	bucket: String,
	nuid: String,
): String = toObjectStoreChunkPrefix(bucket) + nuid

@OptIn(ExperimentalEncodingApi::class)
internal fun encodeObjectNameForSubject(name: String): String = Base64.UrlSafe.encode(name.encodeToByteArray())

internal fun metaSubjectFor(
	bucket: String,
	objectName: String,
): String = toObjectStoreMetaPrefix(bucket) + encodeObjectNameForSubject(objectName)

internal fun StreamInfo.asObjectStoreStatus(): ObjectStoreStatus =
	ObjectStoreStatus(
		bucket = config.name.removePrefix(OBJ_BUCKET_STREAM_NAME_PREFIX),
		description = config.description,
		size = state.bytes,
		ttl = config.maxAge,
		backingStore = "JetStream",
		isCompressed = config.compression != null && config.compression != StreamCompression.None,
		isSealed = config.sealed == true,
	)

internal fun StreamInfo.asObjectStoreConfig(): ObjectStoreConfig =
	ObjectStoreConfig(
		bucket = config.name.removePrefix(OBJ_BUCKET_STREAM_NAME_PREFIX),
		description = config.description,
		maxBytes = config.maxBytes,
		ttl = config.maxAge,
		storage = config.storage,
		replicas = config.replicas,
		placement = config.placement,
		compression = config.compression,
		metadata = config.metadata,
	)

internal fun ObjectStoreConfig.asStreamConfig(): StreamConfig {
	val replicas = (replicas ?: 1).let { if (it == 0) 1 else it }
	val maxBytes = (maxBytes ?: -1L).let { if (it == 0L) -1L else it }

	return StreamConfig(
		name = toObjectStoreStreamName(bucket),
		description = description,
		subjects =
			listOf(
				toObjectStoreMetaStreamSubject(bucket),
				toObjectStoreChunkStreamSubject(bucket),
			),
		maxConsumers = -1,
		maxMessages = -1,
		maxBytes = maxBytes,
		maxAge = ttl,
		storage = storage,
		discard = DiscardPolicy.New,
		replicas = replicas,
		placement = placement,
		allowRollup = true,
		allowDirect = true,
		compression = compression,
		metadata = metadata,
	)
}

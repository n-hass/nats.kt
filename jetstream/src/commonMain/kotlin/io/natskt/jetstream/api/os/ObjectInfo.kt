package io.natskt.jetstream.api.os

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Instant

/**
 * Information about a single object stored in an [ObjectStoreBucket].
 *
 * Mirrors the Object Store wire format used by every NATS client, so Kotlin
 * can interoperate with Go/Java/JS over the same bucket. Field order matches
 * the canonical encoding emitted by `ObjectInfo.embedJson` in nats.java.
 *
 * [modified] is populated from the JetStream message timestamp after decoding
 * and is therefore not part of the JSON payload.
 */
@Serializable
public data class ObjectInfo(
	public val name: String,
	public val description: String? = null,
	public val headers: Map<String, List<String>> = emptyMap(),
	public val metadata: Map<String, String> = emptyMap(),
	public val options: ObjectMetaOptions? = null,
	public val bucket: String,
	public val nuid: String? = null,
	public val size: Long = 0,
	public val chunks: Long = 0,
	public val digest: String? = null,
	public val deleted: Boolean = false,
	@Transient
	public val modified: Instant? = null,
) {
	public val isLink: Boolean get() = options?.link != null
}

@Serializable
public data class ObjectMetaOptions(
	public val link: ObjectLink? = null,
	@SerialName("max_chunk_size")
	public val maxChunkSize: Int = -1,
) {
	public companion object {
		public val Empty: ObjectMetaOptions = ObjectMetaOptions()
	}

	public val hasData: Boolean get() = link != null || maxChunkSize > 0
}

@Serializable
public data class ObjectLink(
	public val bucket: String,
	public val name: String? = null,
) {
	public val isObjectLink: Boolean get() = name != null
	public val isBucketLink: Boolean get() = name == null

	public companion object {
		public fun bucket(bucket: String): ObjectLink = ObjectLink(bucket = bucket, name = null)

		public fun objectLink(
			bucket: String,
			name: String,
		): ObjectLink = ObjectLink(bucket = bucket, name = name)
	}
}

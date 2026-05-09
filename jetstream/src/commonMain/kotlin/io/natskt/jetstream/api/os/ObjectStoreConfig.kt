package io.natskt.jetstream.api.os

import io.natskt.jetstream.api.StorageType
import io.natskt.jetstream.api.StreamCompression
import io.natskt.jetstream.api.StreamPlacement
import io.natskt.jetstream.api.internal.DurationNanosSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * Persisted configuration for an Object Store bucket. Mirrors the user-facing
 * surface from `ObjectStoreConfiguration` in nats.java but uses Kotlin idioms
 * (`StreamCompression?` instead of a boolean, nullable replicas).
 */
@Serializable
public data class ObjectStoreConfig(
	val bucket: String,
	val description: String? = null,
	@SerialName("max_bytes")
	val maxBytes: Long? = null,
	@Serializable(with = DurationNanosSerializer::class)
	val ttl: Duration? = null,
	val storage: StorageType? = null,
	@SerialName("num_replicas")
	val replicas: Int? = null,
	val placement: StreamPlacement? = null,
	val compression: StreamCompression? = null,
	val metadata: Map<String, String>? = null,
)

/**
 * Runtime status of an Object Store bucket, derived from the backing
 * JetStream stream. Matches `ObjectStoreStatus` in nats.java.
 */
public data class ObjectStoreStatus(
	public val bucket: String,
	public val description: String? = null,
	public val size: ULong,
	public val ttl: Duration?,
	public val backingStore: String,
	public val isCompressed: Boolean,
	public val isSealed: Boolean,
)

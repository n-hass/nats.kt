package io.natskt.jetstream.api.kv

import io.natskt.jetstream.api.StorageType
import io.natskt.jetstream.api.StreamCompression
import io.natskt.jetstream.api.StreamPlacement
import io.natskt.jetstream.api.StreamRepublish
import io.natskt.jetstream.api.StreamSource
import io.natskt.jetstream.api.internal.DurationNanosSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
public data class KeyValueConfig(
	val bucket: String,
	val description: String? = null,
	@SerialName("max_value_size")
	val maxValueSize: Int? = null,
	val history: UShort? = null,
	@Serializable(with = DurationNanosSerializer::class)
	val ttl: Duration? = null,
	@SerialName("max_bytes")
	val maxBytes: Long? = null,
	val storage: StorageType? = null,
	@SerialName("num_replicas")
	val replicas: Int? = null,
	val placement: StreamPlacement? = null,
	val republish: StreamRepublish? = null,
	val mirror: StreamSource? = null,
	val sources: List<StreamSource>? = null,
	val compression: StreamCompression? = null,
	@Serializable(with = DurationNanosSerializer::class)
	val limitMarkerTtl: Duration? = null,
	val metadata: Map<String, String>? = null,
)

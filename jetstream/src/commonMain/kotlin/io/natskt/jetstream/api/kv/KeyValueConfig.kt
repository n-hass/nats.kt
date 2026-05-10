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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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
	@SerialName("limit_marker_ttl")
	@Serializable(with = DurationNanosSerializer::class)
	val limitMarkerTtl: Duration? = null,
	val metadata: Map<String, String>? = null,
)

public data class KeyValueEntry(
	public val bucket: String,
	public val key: String,
	public val value: ByteArray,
	public val revision: ULong,
	public val created: Instant,
	public val delta: ULong,
	public val operation: KeyValueOperation?,
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as KeyValueEntry

		if (bucket != other.bucket) return false
		if (key != other.key) return false
		if (!value.contentEquals(other.value)) return false
		if (revision != other.revision) return false
		if (created != other.created) return false
		if (delta != other.delta) return false
		if (operation != other.operation) return false

		return true
	}

	override fun hashCode(): Int {
		var result = bucket.hashCode()
		result = 31 * result + key.hashCode()
		result = 31 * result + value.contentHashCode()
		result = 31 * result + revision.hashCode()
		result = 31 * result + created.hashCode()
		result = 31 * result + delta.hashCode()
		result = 31 * result + operation.hashCode()
		return result
	}

	internal companion object
}

@Serializable
public enum class KeyValueOperation {
	Put,
	Delete,
	Purge,
}

/**
 * Modifiers that change how a key-value watch behaves.
 *
 * Combine multiple options on [KeyValueWatchConfig.options]; [IncludeHistory] and
 * [UpdatesOnly] are mutually exclusive.
 */
public enum class KeyValueWatchOption {
	/** Drop tombstone entries (`Delete` and `Purge`) from the emitted flow. */
	IgnoreDelete,

	/** Ask the server for headers only (no payload bytes), saving bandwidth on large values. */
	MetaOnly,

	/** Replay every retained revision instead of just the latest per key. */
	IncludeHistory,

	/** Skip the initial snapshot — only emit revisions written after the watch was started. */
	UpdatesOnly,
}

/**
 * Bag of [KeyValueWatchOption] flags plus an optional [fromRevision] start point for a watcher.
 *
 * When [fromRevision] is set, the consumer starts at that stream sequence and any conflicting
 * deliver-policy implied by the options is overridden.
 */
public data class KeyValueWatchConfig(
	public val options: Set<KeyValueWatchOption> = emptySet(),
	public val fromRevision: ULong? = null,
) {
	public companion object {
		public val Default: KeyValueWatchConfig = KeyValueWatchConfig()
	}
}

/**
 * Configures [KeyValueBucket.purgeDeletes].
 *
 * - When [noThreshold] is `true`, all delete and purge markers are removed regardless of age.
 * - Otherwise [deleteMarkersThreshold] controls the cutoff; markers older than the threshold
 *   are removed entirely while markers within the window have their history truncated but the
 *   marker itself is retained. Defaults to 30 minutes, matching the Go and Java clients.
 */
public data class KeyValuePurgeOptions(
	public val deleteMarkersThreshold: Duration = 30.minutes,
	public val noThreshold: Boolean = false,
) {
	public companion object {
		public val Default: KeyValuePurgeOptions = KeyValuePurgeOptions()
	}
}

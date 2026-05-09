package io.natskt.jetstream.api.kv

import io.natskt.api.SubjectToken
import io.natskt.api.from
import io.natskt.jetstream.api.StorageType
import io.natskt.jetstream.api.StreamCompression
import io.natskt.jetstream.api.StreamPlacement
import io.natskt.jetstream.api.StreamRepublish
import io.natskt.jetstream.api.StreamSource
import io.natskt.jetstream.api.stream.StreamPlacementBuilder
import io.natskt.jetstream.api.stream.StreamRepublishBuilder
import io.natskt.jetstream.api.stream.StreamSourceBuilder
import io.natskt.jetstream.api.stream.build
import io.natskt.jetstream.internal.JetStreamDsl
import io.natskt.jetstream.internal.KV_BUCKET_MAX_HISTORY_SIZE
import kotlin.time.Duration

@JetStreamDsl
public class KeyValueConfigurationBuilder internal constructor() {
	public var name: String = ""
	public var description: String? = null
	public var history: Int = 1
	public var maxValueSize: Int? = null
	public var maxBytes: Long? = null
	public var ttl: Duration? = null
	public var storage: StorageType? = null
	public var replicas: Int? = null
	public var compression: StreamCompression? = null
	public var limitMarkerTtl: Duration? = null
	public var metadata: MutableMap<String, String>? = null
	public var placement: StreamPlacement? = null
	public var republish: StreamRepublish? = null
	public var mirror: StreamSource? = null
	public var sources: MutableList<StreamSource>? = null

	public fun placement(builder: StreamPlacementBuilder.() -> Unit) {
		placement = StreamPlacementBuilder().apply(builder).build()
	}

	public fun republish(builder: StreamRepublishBuilder.() -> Unit) {
		republish = StreamRepublishBuilder().apply(builder).build()
	}

	public fun mirror(builder: StreamSourceBuilder.() -> Unit) {
		mirror = StreamSourceBuilder(contextStreamName = name).apply(builder).build()
	}

	public fun source(builder: StreamSourceBuilder.() -> Unit) {
		if (sources == null) {
			sources = mutableListOf()
		}
		sources!!.add(StreamSourceBuilder(contextStreamName = name).apply(builder).build())
	}

	public fun metadata(
		key: String,
		value: String,
	) {
		if (metadata == null) {
			metadata = mutableMapOf()
		}
		metadata!![key] = value
	}
}

internal fun KeyValueConfigurationBuilder.build(): KeyValueConfig {
	if (name.isBlank()) error("bucket name must be set")
	SubjectToken.from(name) // parse as token to validate the name
	require(history in 1..KV_BUCKET_MAX_HISTORY_SIZE.toInt()) {
		"history must be between 1 and $KV_BUCKET_MAX_HISTORY_SIZE (got $history)"
	}
	return KeyValueConfig(
		bucket = name,
		description = description,
		maxValueSize = maxValueSize,
		history = history.toUShort(),
		ttl = ttl,
		maxBytes = maxBytes,
		storage = storage,
		replicas = replicas,
		placement = placement,
		republish = republish,
		mirror = mirror,
		sources = sources?.toList(),
		compression = compression,
		limitMarkerTtl = limitMarkerTtl,
		metadata = metadata?.toMap(),
	)
}

package io.natskt.jetstream.api.os

import io.natskt.api.SubjectToken
import io.natskt.api.from
import io.natskt.jetstream.api.StorageType
import io.natskt.jetstream.api.StreamCompression
import io.natskt.jetstream.api.StreamPlacement
import io.natskt.jetstream.internal.JetStreamDsl
import kotlin.time.Duration

@JetStreamDsl
public class ObjectStoreConfigurationBuilder internal constructor() {
	public var name: String = ""
	public var description: String? = null
	public var maxBytes: Long? = null
	public var ttl: Duration? = null
	public var storage: StorageType? = null
	public var replicas: Int? = null
	public var placement: StreamPlacement? = null
	public var compression: StreamCompression? = null
	public var metadata: Map<String, String>? = null

	internal constructor(existing: ObjectStoreConfig) : this() {
		name = existing.bucket
		description = existing.description
		maxBytes = existing.maxBytes
		ttl = existing.ttl
		storage = existing.storage
		replicas = existing.replicas
		placement = existing.placement
		compression = existing.compression
		metadata = existing.metadata
	}
}

internal fun ObjectStoreConfigurationBuilder.build(): ObjectStoreConfig {
	if (name.isBlank()) error("bucket name must be set")
	SubjectToken.from(name)
	return ObjectStoreConfig(
		bucket = name,
		description = description,
		maxBytes = maxBytes,
		ttl = ttl,
		storage = storage,
		replicas = replicas,
		placement = placement,
		compression = compression,
		metadata = metadata,
	)
}

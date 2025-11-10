package io.natskt.jetstream.api.kv

import io.natskt.api.SubjectToken
import io.natskt.api.from
import io.natskt.jetstream.internal.JetStreamDsl

@JetStreamDsl
public class KeyValueConfigurationBuilder internal constructor() {
	public var name: String = ""
	public var history: Int = 1
}

internal fun KeyValueConfigurationBuilder.build(): KeyValueConfig {
	if (name.isBlank()) error("bucket name must be set")
	SubjectToken.from(name) // parse as token to validate the name
	return KeyValueConfig(
		bucket = name,
		history = history.toUShort(),
	)
}

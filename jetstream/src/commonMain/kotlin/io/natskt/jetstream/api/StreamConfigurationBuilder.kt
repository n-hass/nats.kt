package io.natskt.jetstream.api

internal interface StreamConfigurationValues {
	val name: String?
}

public class StreamConfigurationBuilder internal constructor() : StreamConfigurationValues {
	public override var name: String? = null
}

internal fun StreamConfigurationBuilder.build(): StreamConfiguration =
	StreamConfiguration(
		name = this.name!!,
	)

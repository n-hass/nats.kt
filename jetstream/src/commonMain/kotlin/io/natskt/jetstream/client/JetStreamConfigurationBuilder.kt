package io.natskt.jetstream.client

import io.ktor.http.URLBuilder
import io.natskt.client.NatsServerAddress
import io.natskt.jetstream.internal.JetStreamDsl

@JetStreamDsl
public class JetStreamConfigurationBuilder internal constructor() {
	public var apiPrefix: String = "\$JS.API."
	public var domain: String? = null
}

internal fun JetStreamConfigurationBuilder.build(): JetStreamConfiguration {
	val apiPrefix =
		if (!apiPrefix.endsWith(".")) {
			"$apiPrefix."
		} else {
			apiPrefix
		}
	return JetStreamConfiguration(
		prefix = apiPrefix.removeSuffix("API."),
		apiPrefix = apiPrefix,
		domain = domain,
	)
}

private fun parseUrl(raw: String): NatsServerAddress =
	NatsServerAddress(
		URLBuilder(raw)
			.build(),
	)

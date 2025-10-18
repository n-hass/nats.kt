package io.natskt.jetstream.client

import io.ktor.http.URLBuilder
import io.natskt.client.NatsServerAddress

internal interface JetStreamConfigurationValues {
	val apiPrefix: String
	val domain: String?
}

public class JetStreamConfigurationBuilder internal constructor() : JetStreamConfigurationValues {
	override var apiPrefix: String = "\$JS.API."
	override var domain: String? = null
}

internal fun JetStreamConfigurationBuilder.build(): JetStreamConfiguration {
	val apiPrefix =
		if (!apiPrefix.endsWith(".")) {
			"$apiPrefix."
		} else {
			apiPrefix
		}
	return JetStreamConfiguration(
		apiPrefix = apiPrefix,
		domain = domain,
	)
}

private fun parseUrl(raw: String): NatsServerAddress =
	NatsServerAddress(
		URLBuilder(raw)
			.build(),
	)

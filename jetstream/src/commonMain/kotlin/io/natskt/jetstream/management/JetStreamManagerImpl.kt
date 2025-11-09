package io.natskt.jetstream.management

import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.JetStreamManager
import io.natskt.jetstream.api.stream.Stream
import io.natskt.jetstream.api.stream.StreamConfigurationBuilder
import io.natskt.jetstream.api.stream.build
import io.natskt.jetstream.internal.StreamImpl
import io.natskt.jetstream.internal.createStream

internal class JetStreamManagerImpl(
	private val js: JetStreamClient,
) : JetStreamManager {
	override suspend fun createStream(configure: StreamConfigurationBuilder.() -> Unit): Stream {
		val configuration = StreamConfigurationBuilder().apply(configure).build()

		return js.createStream(configuration).fold(
			onSuccess = {
				StreamImpl(
					configuration.name,
					js,
					it,
				)
			},
			onFailure = {
				throw it
			},
		)
	}
}

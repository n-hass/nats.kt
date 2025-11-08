package io.natskt.jetstream.management

import io.natskt.api.Subscription
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.JetStreamManagement
import io.natskt.jetstream.api.stream.Stream
import io.natskt.jetstream.api.stream.StreamConfigurationBuilder
import io.natskt.jetstream.api.stream.build
import io.natskt.jetstream.internal.PersistentRequestSubscription
import io.natskt.jetstream.internal.StreamImpl
import io.natskt.jetstream.internal.createStream

internal class JetStreamManagementImpl(
	js: JetStreamClient,
	inboxSubscription: Subscription,
) : PersistentRequestSubscription(js, inboxSubscription),
	JetStreamManagement {
	override suspend fun createStream(configure: StreamConfigurationBuilder.() -> Unit): Stream {
		val configuration = StreamConfigurationBuilder().apply(configure).build()

		return createStream(configuration).fold(
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

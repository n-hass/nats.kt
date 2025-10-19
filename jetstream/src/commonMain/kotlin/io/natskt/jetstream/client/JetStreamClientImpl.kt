@file:OptIn(InternalNatsApi::class)

package io.natskt.jetstream.client

import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.api.internal.InternalNatsApi
import io.natskt.client.ByteMessageBuilder
import io.natskt.client.StringMessageBuilder
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.PublishAck
import io.natskt.jetstream.api.PullConsumer
import io.natskt.jetstream.api.Stream
import io.natskt.jetstream.api.StreamConfigurationBuilder
import io.natskt.jetstream.api.build
import io.natskt.jetstream.internal.StreamImpl
import io.natskt.jetstream.internal.creatStream

internal class JetStreamClientImpl(
	internal val client: NatsClient,
	internal val config: JetStreamConfiguration,
) : JetStreamClient {
	override suspend fun publish(
		subject: String,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: String?,
	): PublishAck {
		TODO("Not yet implemented")
	}

	override suspend fun publish(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: Subject?,
	): PublishAck {
		TODO("Not yet implemented")
	}

	override suspend fun publish(message: Message): PublishAck {
		TODO("Not yet implemented")
	}

	override suspend fun publishBytes(byteMessageBlock: ByteMessageBuilder.() -> Unit): PublishAck {
		TODO("Not yet implemented")
	}

	override suspend fun publishString(stringMessageBlock: StringMessageBuilder.() -> Unit): PublishAck {
		TODO("Not yet implemented")
	}

	override suspend fun pull(
		streamName: String,
		consumerName: String,
	): PullConsumer {
		TODO("Not yet implemented")
	}

	override suspend fun stream(name: String): Stream =
		StreamImpl(
			name,
			this,
			null,
		).also { it.updateStreamInfo() }

	override suspend fun stream(configure: StreamConfigurationBuilder.() -> Unit): Stream {
		val configuration = StreamConfigurationBuilder().apply(configure).build()

		return creatStream(configuration).fold(
			onSuccess = {
				StreamImpl(
					configuration.name,
					this,
					it,
				)
			},
			onFailure = {
				throw it
			},
		)
	}
}

package io.natskt.jetstream.internal

import io.natskt.api.internal.InternalNatsApi
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.consumer.ConsumerConfigurationBuilder
import io.natskt.jetstream.api.consumer.PullConsumer
import io.natskt.jetstream.api.consumer.build
import io.natskt.jetstream.api.stream.Stream
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(InternalNatsApi::class)
internal class StreamImpl(
	private val name: String,
	private val js: JetStreamClient,
	initialInfo: StreamInfo?,
) : Stream {
	@OptIn(InternalNatsApi::class)
	override val info = MutableStateFlow(initialInfo)

	override suspend fun updateStreamInfo(): Result<StreamInfo> {
		val new = js.getStreamInfo(name)
		new.onSuccess {
			info.value = it
		}
		return new
	}

	override suspend fun pullConsumer(name: String): PullConsumer =
		PullConsumerImpl(
			name = name,
			streamName = this.name,
			js = js,
			initialInfo = null,
		)

	override suspend fun createPullConsumer(configure: ConsumerConfigurationBuilder.() -> Unit): PullConsumer {
		val new = client.createOrUpdateConsumer(name, ConsumerConfigurationBuilder().apply(configure).build())
		return new
			.map {
				PullConsumerImpl(
					name = it.name,
					streamName = it.stream,
					js = client,
					initialInfo = it,
				)
			}.getOrThrow()
	}
}

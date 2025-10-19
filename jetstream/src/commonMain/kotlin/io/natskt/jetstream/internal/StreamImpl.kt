package io.natskt.jetstream.internal

import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.NUID
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.consumer.ConsumerConfigurationBuilder
import io.natskt.jetstream.api.consumer.PullConsumer
import io.natskt.jetstream.api.consumer.build
import io.natskt.jetstream.api.stream.Stream
import io.natskt.jetstream.client.JetStreamClientImpl
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(InternalNatsApi::class)
internal class StreamImpl(
	private val name: String,
	private val client: JetStreamClientImpl,
	initialInfo: StreamInfo?,
) : Stream {
	@OptIn(InternalNatsApi::class)
	override val info = MutableStateFlow(initialInfo)

	override suspend fun updateStreamInfo(): Result<StreamInfo> {
		val new = client.getStreamInfo(name)
		new.onSuccess {
			info.value = it
		}
		return new
	}

	override suspend fun pullConsumer(name: String): PullConsumer =
		PullConsumerImpl(
			name = name,
			streamName = this.name,
			client = client,
			deliverSubjectPrefix = NUID.next() + ".",
			initialInfo = null,
		)

	override suspend fun createPullConsumer(configure: ConsumerConfigurationBuilder.() -> Unit): PullConsumer {
		val new = client.createOrUpdateConsumer(name, ConsumerConfigurationBuilder().apply(configure).build())
		return new
			.map {
				PullConsumerImpl(
					name = it.name,
					streamName = it.stream,
					client = client,
					deliverSubjectPrefix = NUID.next() + ".",
					initialInfo = it,
				)
			}.getOrThrow()
	}
}

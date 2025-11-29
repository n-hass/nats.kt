package io.natskt.jetstream.internal

import io.natskt.api.ProtocolException
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.throwOnInvalidToken
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.consumer.ConsumerConfigurationBuilder
import io.natskt.jetstream.api.consumer.PullConsumer
import io.natskt.jetstream.api.consumer.PushConsumer
import io.natskt.jetstream.api.consumer.build
import io.natskt.jetstream.api.stream.Stream
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(InternalNatsApi::class)
internal class StreamImpl(
	private val name: String,
	private val js: JetStreamClient,
	initialInfo: StreamInfo?,
) : Stream {
	init {
		name.throwOnInvalidToken()
	}

	@OptIn(InternalNatsApi::class)
	override val info = MutableStateFlow(initialInfo)

	override suspend fun updateStreamInfo(): Result<StreamInfo> {
		val new = js.getStreamInfo(name)
		new.onSuccess {
			info.value = it
		}
		return new
	}

	override suspend fun createPullConsumer(configure: ConsumerConfigurationBuilder.() -> Unit): PullConsumer {
		val new = js.manager.createOrUpdateConsumer(name, configure)
		return PullConsumerImpl(
			name = new.name,
			streamName = new.stream,
			js = js,
			initialInfo = new,
		)
	}

	override suspend fun createPushConsumer(configure: ConsumerConfigurationBuilder.() -> Unit): PushConsumer {
		val config = ConsumerConfigurationBuilder().apply(configure).build()
		config.durableName?.throwOnInvalidToken()
		val new = js.createOrUpdateConsumer(name, config)
		return new
			.map {
				it.config.deliverSubject ?: throw ProtocolException("ConsumerInfo response from server has no deliver subject set")
				PushConsumerImpl(
					name = it.name,
					streamName = it.stream,
					js = js,
					subscription = PushConsumerImpl.newSubscription(js.client, it.config.deliverSubject),
					initialInfo = it,
				)
			}.getOrThrow()
	}
}

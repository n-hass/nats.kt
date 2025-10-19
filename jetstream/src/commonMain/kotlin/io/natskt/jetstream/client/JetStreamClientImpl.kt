@file:OptIn(InternalNatsApi::class)

package io.natskt.jetstream.client

import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.api.Subscription
import io.natskt.api.internal.InternalNatsApi
import io.natskt.client.ByteMessageBuilder
import io.natskt.client.StringMessageBuilder
import io.natskt.internal.NUID
import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.ApiResponse
import io.natskt.jetstream.api.JetStreamApiResponse
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.PublishAck
import io.natskt.jetstream.api.consumer.PullConsumer
import io.natskt.jetstream.api.internal.decodeApiResponse
import io.natskt.jetstream.api.stream.Stream
import io.natskt.jetstream.api.stream.StreamConfigurationBuilder
import io.natskt.jetstream.api.stream.build
import io.natskt.jetstream.internal.StreamImpl
import io.natskt.jetstream.internal.createStream
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

internal class JetStreamClientImpl(
	override val client: NatsClient,
	internal val config: JetStreamConfiguration,
	internal val inboxSubscription: Subscription,
) : JetStreamClient {
	val inboxPrefix = inboxSubscription.subject.raw.dropLast(1)

	suspend inline fun <reified T : JetStreamApiResponse> request(
		subject: String,
		message: String?,
		headers: Map<String, List<String>>? = null,
	): ApiResponse {
		val replyTo = inboxPrefix + NUID.nextSequence()
		val response =
			inboxSubscription.messages
				.filter { it.subject.raw == replyTo }
				.onStart {
					client.publish(subject, message?.encodeToByteArray(), headers, replyTo = replyTo)
				}.first()
		if (response.data == null || response.data!!.isEmpty()) {
			return ApiError(code = response.status)
		}
		return wireJsonFormat.decodeApiResponse<T>(response.data!!.decodeToString())
	}

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

	override suspend fun createStream(configure: StreamConfigurationBuilder.() -> Unit): Stream {
		val configuration = StreamConfigurationBuilder().apply(configure).build()

		return createStream(configuration).fold(
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

	override fun close() {
		client.scope.launch { inboxSubscription.unsubscribe() }
	}
}

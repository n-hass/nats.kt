package io.natskt.jetstream.management

import io.natskt.internal.throwOnInvalidToken
import io.natskt.jetstream.api.AccountInfo
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.ConsumerPauseResponse
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.JetStreamManager
import io.natskt.jetstream.api.MessageGetRequest
import io.natskt.jetstream.api.PurgeOptions
import io.natskt.jetstream.api.PurgeResponse
import io.natskt.jetstream.api.StoredMessage
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.StreamInfoOptions
import io.natskt.jetstream.api.consumer.ConsumerConfigurationBuilder
import io.natskt.jetstream.api.consumer.build
import io.natskt.jetstream.api.stream.Stream
import io.natskt.jetstream.api.stream.StreamConfigurationBuilder
import io.natskt.jetstream.api.stream.build
import io.natskt.jetstream.internal.StreamImpl
import io.natskt.jetstream.internal.createConsumer
import io.natskt.jetstream.internal.createOrUpdateConsumer
import io.natskt.jetstream.internal.createStream
import io.natskt.jetstream.internal.deleteConsumer
import io.natskt.jetstream.internal.deleteMessage
import io.natskt.jetstream.internal.deleteStream
import io.natskt.jetstream.internal.getAccountInfo
import io.natskt.jetstream.internal.getConsumerInfo
import io.natskt.jetstream.internal.getConsumerNames
import io.natskt.jetstream.internal.getConsumers
import io.natskt.jetstream.internal.getMessageDirect
import io.natskt.jetstream.internal.getMessageInfo
import io.natskt.jetstream.internal.getStreamInfo
import io.natskt.jetstream.internal.getStreamNames
import io.natskt.jetstream.internal.getStreams
import io.natskt.jetstream.internal.pauseConsumer
import io.natskt.jetstream.internal.purgeStream
import io.natskt.jetstream.internal.resumeConsumer
import io.natskt.jetstream.internal.updateConsumer
import io.natskt.jetstream.internal.updateStream
import kotlin.time.Instant

internal class JetStreamManagerImpl(
	private val js: JetStreamClient,
) : JetStreamManager {
	// Account Operations

	override suspend fun getAccountStatistics(): AccountInfo = js.getAccountInfo().getOrThrow()

	// Stream Operations

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

	override suspend fun updateStream(
		streamName: String,
		configure: StreamConfigurationBuilder.() -> Unit,
	): StreamInfo {
		val configuration =
			StreamConfigurationBuilder()
				.apply {
					name = streamName
					configure()
				}.build()
		return js.updateStream(configuration).getOrThrow()
	}

	override suspend fun deleteStream(streamName: String): Boolean = js.deleteStream(streamName).getOrThrow()

	override suspend fun getStreamInfo(
		streamName: String,
		options: StreamInfoOptions?,
	): StreamInfo = js.getStreamInfo(streamName, options).getOrThrow()

	override suspend fun purgeStream(streamName: String): PurgeResponse = js.purgeStream(streamName, null).getOrThrow()

	override suspend fun purgeStream(
		streamName: String,
		options: PurgeOptions,
	): PurgeResponse = js.purgeStream(streamName, options).getOrThrow()

	override suspend fun getStreamNames(subjectFilter: String?): List<String> = js.getStreamNames(subjectFilter).getOrThrow()

	override suspend fun getStreams(subjectFilter: String?): List<StreamInfo> = js.getStreams(subjectFilter).getOrThrow()

	// Consumer Operations

	override suspend fun createOrUpdateConsumer(
		streamName: String,
		configure: ConsumerConfigurationBuilder.() -> Unit,
	): ConsumerInfo {
		val configuration = ConsumerConfigurationBuilder().apply(configure).build()
		return js.createOrUpdateConsumer(streamName, configuration).getOrThrow()
	}

	override suspend fun createConsumer(
		streamName: String,
		configure: ConsumerConfigurationBuilder.() -> Unit,
	): ConsumerInfo {
		val configuration = ConsumerConfigurationBuilder().apply(configure).build()
		return js.createConsumer(streamName, configuration).getOrThrow()
	}

	override suspend fun updateConsumer(
		streamName: String,
		configure: ConsumerConfigurationBuilder.() -> Unit,
	): ConsumerInfo {
		streamName.throwOnInvalidToken()
		val configuration = ConsumerConfigurationBuilder().apply(configure).build()
		return js.updateConsumer(streamName, configuration).getOrThrow()
	}

	override suspend fun deleteConsumer(
		streamName: String,
		consumerName: String,
	): Boolean {
		streamName.throwOnInvalidToken()
		consumerName.throwOnInvalidToken()
		js.deleteConsumer(streamName, consumerName).getOrThrow()
		return true
	}

	override suspend fun pauseConsumer(
		streamName: String,
		consumerName: String,
		pauseUntil: Instant,
	): ConsumerPauseResponse {
		streamName.throwOnInvalidToken()
		consumerName.throwOnInvalidToken()
		return js.pauseConsumer(streamName, consumerName, pauseUntil).getOrThrow()
	}

	override suspend fun resumeConsumer(
		streamName: String,
		consumerName: String,
	): Boolean {
		streamName.throwOnInvalidToken()
		consumerName.throwOnInvalidToken()
		return js.resumeConsumer(streamName, consumerName).getOrThrow()
	}

	override suspend fun getConsumerInfo(
		streamName: String,
		consumerName: String,
	): ConsumerInfo {
		streamName.throwOnInvalidToken()
		consumerName.throwOnInvalidToken()
		return js.getConsumerInfo(streamName, consumerName).getOrThrow()
	}

	override suspend fun getConsumerNames(streamName: String): List<String> {
		streamName.throwOnInvalidToken()
		return js.getConsumerNames(streamName).getOrThrow()
	}

	override suspend fun getConsumers(streamName: String): List<ConsumerInfo> {
		streamName.throwOnInvalidToken()
		return js.getConsumers(streamName).getOrThrow()
	}

	// Message Operations

	override suspend fun getMessage(
		streamName: String,
		sequence: ULong,
		direct: Boolean,
	): StoredMessage {
		streamName.throwOnInvalidToken()
		val req = MessageGetRequest(seq = sequence)
		return if (direct) {
			js.getMessageDirect(streamName, req).getOrThrow()
		} else {
			js.getMessageInfo(streamName, req).getOrThrow().message
		}
	}

	override suspend fun getMessage(
		streamName: String,
		request: MessageGetRequest,
		direct: Boolean,
	): StoredMessage {
		streamName.throwOnInvalidToken()
		return if (direct) {
			js.getMessageDirect(streamName, request).getOrThrow()
		} else {
			js.getMessageInfo(streamName, request).getOrThrow().message
		}
	}

	override suspend fun getLastMessage(
		streamName: String,
		subject: String,
		direct: Boolean,
	): StoredMessage {
		streamName.throwOnInvalidToken()
		val req = MessageGetRequest(lastFor = subject)
		return if (direct) {
			js.getMessageDirect(streamName, req).getOrThrow()
		} else {
			js.getMessageInfo(streamName, req).getOrThrow().message
		}
	}

	override suspend fun getNextMessage(
		streamName: String,
		sequence: ULong,
		subject: String,
		direct: Boolean,
	): StoredMessage {
		streamName.throwOnInvalidToken()
		val req = MessageGetRequest(seq = sequence, nextFor = subject)
		return if (direct) {
			js.getMessageDirect(streamName, req).getOrThrow()
		} else {
			js.getMessageInfo(streamName, req).getOrThrow().message
		}
	}

	override suspend fun deleteMessage(
		streamName: String,
		sequence: ULong,
		erase: Boolean,
	): Boolean {
		streamName.throwOnInvalidToken()
		return js.deleteMessage(streamName, sequence, erase).getOrThrow()
	}
}

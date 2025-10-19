package io.natskt.jetstream.internal

import io.natskt.api.Message
import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.ConsumerConfiguration
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.JetStreamUnknownResponseException
import io.natskt.jetstream.api.PullConsumerResponse
import io.natskt.jetstream.api.StreamConfiguration
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.consumer.ConsumerCreateAction
import io.natskt.jetstream.api.consumer.ConsumerCreateRequest
import io.natskt.jetstream.client.JetStreamClientImpl

internal const val STREAM_INFO = "STREAM.INFO."
internal const val STREAM_CREATE = "STREAM.CREATE."
internal const val CONSUMER_INFO = "CONSUMER.INFO."

internal suspend fun JetStreamClientImpl.getStreamInfo(
	name: String,
	subjectFilter: String? = null,
	deletedDetails: Boolean = false,
): Result<StreamInfo> {
	val data = request<StreamInfo>(config.apiPrefix + STREAM_INFO + name, null)
	return when (data) {
		is StreamInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun JetStreamClientImpl.createStream(configuration: StreamConfiguration): Result<StreamInfo> {
	val data = request<StreamInfo>(config.apiPrefix + STREAM_CREATE + configuration.name, wireJsonFormat.encodeToString(configuration))
	return when (data) {
		is StreamInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun JetStreamClientImpl.createOrUpdateConsumer(
	streamName: String,
	configuration: ConsumerConfiguration,
): Result<ConsumerInfo> {
	val subject =
		when {
			configuration.durableName != null && configuration.filterSubject != null ->
				config.apiPrefix + "CONSUMER.CREATE." + streamName + "." + configuration.durableName + "." +
					configuration.filterSubject
			configuration.durableName != null -> config.apiPrefix + "CONSUMER.CREATE." + streamName + "." + configuration.durableName
			else -> config.apiPrefix + "CONSUMER.CREATE." + streamName
		}

	val payload =
		wireJsonFormat.encodeToString(
			ConsumerCreateRequest(
				streamName = streamName,
				config = configuration,
				action = ConsumerCreateAction.CreateOrUpdate,
			),
		)

	val data = request<ConsumerInfo>(subject, payload)

	return when (data) {
		is ConsumerInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun JetStreamClientImpl.pull(
	streamName: String,
	consumerName: String,
	requestBody: String,
): Message? {
	val subject =
		buildString {
			append(config.apiPrefix)
			append("CONSUMER.MSG.NEXT.")
			append(streamName)
			append(".")
			append(consumerName)
		}
	val msg = request<PullConsumerResponse>(subject, requestBody)
	when (msg) {
		is PullConsumerResponse ->
			msg.messages.forEach {
				println(it)
			}
		is ApiError -> {
			return null
		}
		else -> throw JetStreamUnknownResponseException(msg)
	}

	return IncomingJetStreamMessage(
		sid = TODO(),
		subjectString = TODO(),
		replyToString = TODO(),
		headers = TODO(),
		data = TODO(),
		ack = TODO(),
		nak = TODO(),
		metadata = TODO(),
		status = TODO(),
	)
}

internal suspend fun JetStreamClientImpl.getConsumerInfo(
	streamName: String,
	name: String,
): Result<ConsumerInfo> {
	val subject =
		buildString {
			append(config.apiPrefix)
			append(CONSUMER_INFO)
			append(streamName)
			append(".")
			append(name)
		}
	val data = request<ConsumerInfo>(subject, null)
	return when (data) {
		is ConsumerInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

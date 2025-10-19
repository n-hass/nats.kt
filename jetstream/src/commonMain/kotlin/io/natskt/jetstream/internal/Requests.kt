package io.natskt.jetstream.internal

import io.natskt.api.Message
import io.natskt.internal.NUID
import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.ConsumerConfiguration
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.JetStreamUnknownResponseException
import io.natskt.jetstream.api.StreamConfiguration
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.consumer.ConsumerCreateAction
import io.natskt.jetstream.api.consumer.ConsumerCreateRequest
import io.natskt.jetstream.api.internal.decodeApiResponse
import io.natskt.jetstream.client.JetStreamClientImpl

internal const val STREAM_INFO = "STREAM.INFO."
internal const val STREAM_CREATE = "STREAM.CREATE."
internal const val CONSUMER_INFO = "CONSUMER.INFO."

internal suspend fun JetStreamClientImpl.getStreamInfo(
	name: String,
	subjectFilter: String? = null,
	deletedDetails: Boolean = false,
): Result<StreamInfo> {
	val data = client.request(config.apiPrefix + STREAM_INFO + name, null).data
	if (data == null) return Result.failure(JetStreamApiException(null, message = "response was empty"))
	return when (val r = wireJsonFormat.decodeApiResponse<StreamInfo>(data.decodeToString())) {
		is StreamInfo -> Result.success(r)
		is ApiError -> Result.failure(JetStreamApiException(r))
		else -> Result.failure(JetStreamUnknownResponseException(r))
	}
}

internal suspend fun JetStreamClientImpl.creatStream(configuration: StreamConfiguration): Result<StreamInfo> {
	val data = client.request(config.apiPrefix + STREAM_CREATE + configuration.name, wireJsonFormat.encodeToString(configuration).encodeToByteArray()).data
	if (data == null) return Result.failure(JetStreamApiException(null, message = "response was empty"))
	return when (val r = wireJsonFormat.decodeApiResponse<StreamInfo>(data.decodeToString())) {
		is StreamInfo -> Result.success(r)
		is ApiError -> Result.failure(JetStreamApiException(r))
		else -> Result.failure(JetStreamUnknownResponseException(r))
	}
}

internal suspend fun JetStreamClientImpl.createOrUpdateConsumer(
	streamName: String,
	configuration: ConsumerConfiguration,
): Result<ConsumerInfo> {
	val subject =
		if (configuration.durableName != null) {
			config.apiPrefix + "CONSUMER.CREATE." + streamName + "." + configuration.durableName
		} else {
			config.apiPrefix + "CONSUMER.CREATE." + streamName
		}

	val payload =
		ConsumerCreateRequest(
			stream = streamName,
			config = configuration,
			action = ConsumerCreateAction.CreateOrUpdate,
		)

	val data = client.request(subject, wireJsonFormat.encodeToString(payload).encodeToByteArray()).data
	if (data == null) return Result.failure(JetStreamApiException(null, message = "response was empty"))

	val jsonString = data.decodeToString()
	val response = wireJsonFormat.decodeApiResponse<ConsumerInfo>(jsonString)

	return when (response) {
		is ConsumerInfo -> Result.success(response)
		is ApiError -> Result.failure(JetStreamApiException(response))
		else -> Result.failure(JetStreamUnknownResponseException(response))
	}
}

internal suspend fun JetStreamClientImpl.pull(
	deliverSubjectPrefix: String,
	streamName: String,
	consumerName: String,
	requestBody: String,
): Message {
	val subject =
		buildString {
			append(config.apiPrefix)
			append("CONSUMER.MSG.NEXT.")
			append(streamName)
			append(".")
			append(consumerName)
		}
	val reqReplyTo = deliverSubjectPrefix + NUID.nextSequence()
	val msg = client.request(subject, requestBody.encodeToByteArray(), replyTo = reqReplyTo)
	return msg
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
	val data = client.request(subject, null).data
	if (data == null) return Result.failure(JetStreamApiException(null, message = "response was empty"))
	return when (val r = wireJsonFormat.decodeApiResponse<ConsumerInfo>(data.decodeToString())) {
		is ConsumerInfo -> Result.success(r)
		is ApiError -> Result.failure(JetStreamApiException(r))
		else -> Result.failure(JetStreamUnknownResponseException(r))
	}
}

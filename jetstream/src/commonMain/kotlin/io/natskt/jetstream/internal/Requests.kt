package io.natskt.jetstream.internal

import io.natskt.api.Message
import io.natskt.internal.NUID
import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.ApiResponse
import io.natskt.jetstream.api.ConsumerConfiguration
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.JetStreamApiResponse
import io.natskt.jetstream.api.JetStreamUnknownResponseException
import io.natskt.jetstream.api.StreamConfiguration
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.consumer.ConsumerCreateAction
import io.natskt.jetstream.api.consumer.ConsumerCreateRequest
import io.natskt.jetstream.api.internal.decodeApiResponse

internal const val STREAM_INFO = "STREAM.INFO."
internal const val STREAM_CREATE = "STREAM.CREATE."
internal const val CONSUMER_INFO = "CONSUMER.INFO."

internal inline fun <reified T : JetStreamApiResponse> Message.decode(): ApiResponse {
	if (data == null || data!!.isEmpty()) {
		return ApiError(code = status)
	}
	return wireJsonFormat.decodeApiResponse<T>(data!!.decodeToString())
}

internal suspend fun PersistentRequestSubscription.getStreamInfo(
	name: String,
	subjectFilter: String? = null,
	deletedDetails: Boolean = false,
): Result<StreamInfo> {
	val data = request<StreamInfo>(js.config.apiPrefix + STREAM_INFO + name, null)
	return when (data) {
		is StreamInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun PersistentRequestSubscription.createStream(configuration: StreamConfiguration): Result<StreamInfo> {
	val data = request<StreamInfo>(js.config.apiPrefix + STREAM_CREATE + configuration.name, wireJsonFormat.encodeToString(configuration))
	return when (data) {
		is StreamInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun PersistentRequestSubscription.createOrUpdateConsumer(
	streamName: String,
	configuration: ConsumerConfiguration,
): Result<ConsumerInfo> {
	val subject =
		when {
			configuration.durableName != null && configuration.filterSubject != null ->
				js.config.apiPrefix + "CONSUMER.CREATE." + streamName + "." + configuration.durableName + "." +
					configuration.filterSubject
			configuration.durableName != null -> js.config.apiPrefix + "CONSUMER.CREATE." + streamName + "." + configuration.durableName
			else -> js.config.apiPrefix + "CONSUMER.CREATE." + streamName
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

internal suspend fun PersistentRequestSubscription.pull(
	streamName: String,
	consumerName: String,
	requestBody: String,
	replyTo: String?,
): String {
	val subject =
		buildString {
			append(js.config.apiPrefix)
			append("CONSUMER.MSG.NEXT.")
			append(streamName)
			append(".")
			append(consumerName)
		}

	val replyTo = replyTo ?: (inboxPrefix + NUID.nextSequence())
	js.client.publish(subject, requestBody.encodeToByteArray(), replyTo = replyTo)
	return replyTo
}

internal suspend fun PersistentRequestSubscription.getConsumerInfo(
	streamName: String,
	name: String,
): Result<ConsumerInfo> {
	val subject =
		buildString {
			append(js.config.apiPrefix)
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

package io.natskt.jetstream.internal

import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.AccountInfo
import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.ConsumerConfig
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.JetStreamUnknownResponseException
import io.natskt.jetstream.api.StreamConfig
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.consumer.ConsumerCreateAction
import io.natskt.jetstream.api.consumer.ConsumerCreateRequest
import io.natskt.jetstream.api.internal.decode

internal const val STREAM_INFO = "STREAM.INFO."
internal const val STREAM_CREATE = "STREAM.CREATE."
internal const val CONSUMER_INFO = "CONSUMER.INFO."

internal suspend fun CanRequest.getStreamInfo(
	name: String,
	subjectFilter: String? = null,
	deletedDetails: Boolean = false,
): Result<StreamInfo> {
	val data = request(config.apiPrefix + STREAM_INFO + name, null).decode<StreamInfo>()
	return when (data) {
		is StreamInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.createStream(configuration: StreamConfig): Result<StreamInfo> {
	val data = request(config.apiPrefix + STREAM_CREATE + configuration.name, wireJsonFormat.encodeToString(configuration)).decode<StreamInfo>()
	return when (data) {
		is StreamInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.createOrUpdateConsumer(
	streamName: String,
	configuration: ConsumerConfig,
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

	val data = request(subject, payload).decode<ConsumerInfo>()

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
) {
	val subject =
		buildString {
			append(js.config.apiPrefix)
			append("CONSUMER.MSG.NEXT.")
			append(streamName)
			append(".")
			append(consumerName)
		}

	js.client.publish(subject, requestBody.encodeToByteArray(), replyTo = replyTo)
	return
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
	val data = request(subject, null).decode<ConsumerInfo>()
	return when (data) {
		is ConsumerInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.getAccountInfo(): Result<AccountInfo> {
	val subject =
		buildString {
			append(config.apiPrefix)
			append("INFO")
		}
	val data = request(subject, null).decode<AccountInfo>()
	return when (data) {
		is AccountInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

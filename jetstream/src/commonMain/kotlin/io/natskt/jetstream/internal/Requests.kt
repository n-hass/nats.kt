package io.natskt.jetstream.internal

import io.natskt.api.Message
import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.AccountInfo
import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.ConsumerConfig
import io.natskt.jetstream.api.ConsumerDeleteResponse
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.JetStreamUnknownResponseException
import io.natskt.jetstream.api.MessageGetRequest
import io.natskt.jetstream.api.StreamConfig
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.consumer.ConsumerCreateAction
import io.natskt.jetstream.api.consumer.ConsumerCreateRequest
import io.natskt.jetstream.api.internal.decode
import io.natskt.jetstream.api.internal.decodeApiResponse

internal const val STREAM_INFO = "STREAM.INFO."
internal const val STREAM_CREATE = "STREAM.CREATE."
internal const val CONSUMER_CREATE = "CONSUMER.CREATE."
internal const val CONSUMER_INFO = "CONSUMER.INFO."
internal const val CONSUMER_DELETE = "CONSUMER.DELETE."
internal const val DIRECT_GET = "DIRECT.GET."
internal const val MSG_GET = "STREAM.MSG.GET."

internal suspend fun CanRequest.getStreamInfo(name: String): Result<StreamInfo> =
	when (val data = request(context.config.apiPrefix + STREAM_INFO + name, null).decode<StreamInfo>()) {
		is StreamInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}

internal suspend fun CanRequest.createStream(configuration: StreamConfig): Result<StreamInfo> =
	when (val data = request(context.config.apiPrefix + STREAM_CREATE + configuration.name, wireJsonFormat.encodeToString(configuration)).decode<StreamInfo>()) {
		is StreamInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}

internal suspend fun CanRequest.createOrUpdateConsumer(
	streamName: String,
	configuration: ConsumerConfig,
): Result<ConsumerInfo> {
	val subject =
		when {
			configuration.durableName != null && configuration.filterSubject != null ->
				context.config.apiPrefix + CONSUMER_CREATE + streamName + "." + configuration.durableName + "." +
					configuration.filterSubject
			configuration.durableName != null -> context.config.apiPrefix + CONSUMER_CREATE + streamName + "." + configuration.durableName
			else -> context.config.apiPrefix + CONSUMER_CREATE + streamName
		}

	val payload =
		wireJsonFormat.encodeToString(
			ConsumerCreateRequest(
				streamName = streamName,
				config = configuration,
				action = ConsumerCreateAction.CreateOrUpdate,
			),
		)

	return when (val data = request(subject, payload).decode<ConsumerInfo>()) {
		is ConsumerInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.createFilteredConsumer(
	streamName: String,
	consumerName: String,
	filterSubject: String,
	configuration: ConsumerConfig,
): Result<ConsumerInfo> {
	val subject =
		buildString {
			append(context.config.apiPrefix)
			append(CONSUMER_CREATE)
			append(streamName)
			append(".")
			append(consumerName)
			append(".")
			append(filterSubject)
		}

	val payload =
		wireJsonFormat.encodeToString(
			ConsumerCreateRequest(
				streamName = streamName,
				config = configuration,
				action = ConsumerCreateAction.Create,
			),
		)

	return when (val data = request(subject, payload).decode<ConsumerInfo>()) {
		is ConsumerInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.deleteConsumer(
	streamName: String,
	consumerName: String,
): Result<Unit> {
	val subject =
		buildString {
			append(context.config.apiPrefix)
			append(CONSUMER_DELETE)
			append(streamName)
			append(".")
			append(consumerName)
		}

	val response = request(subject, null)

	if (response.data != null && response.data!!.isNotEmpty()) {
		val data = response.data!!.decodeToString()
		val body = wireJsonFormat.decodeApiResponse<ConsumerDeleteResponse>(data)
		return Result.failure(
			when (body) {
				is ApiError -> JetStreamApiException(body)
				else -> JetStreamUnknownResponseException(body)
			},
		)
	}

	return Result.success(Unit)
}

internal suspend fun PersistentRequestSubscription.publishMsgNext(
	streamName: String,
	consumerName: String,
	requestBody: String,
	replyTo: String?,
) {
	val subject =
		buildString {
			append(js.context.config.apiPrefix)
			append("CONSUMER.MSG.NEXT.")
			append(streamName)
			append(".")
			append(consumerName)
		}

	js.client.publish(subject, requestBody.encodeToByteArray(), replyTo = replyTo)
	return
}

internal suspend fun CanRequest.getConsumerInfo(
	streamName: String,
	name: String,
): Result<ConsumerInfo> {
	val subject =
		buildString {
			append(context.config.apiPrefix)
			append(CONSUMER_INFO)
			append(streamName)
			append(".")
			append(name)
		}
	return when (val data = request(subject, null).decode<ConsumerInfo>()) {
		is ConsumerInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.getAccountInfo(): Result<AccountInfo> {
	val subject =
		buildString {
			append(context.config.apiPrefix)
			append("INFO")
		}
	return when (val data = request(subject, null).decode<AccountInfo>()) {
		is AccountInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.getMessage(
	streamName: String,
	req: MessageGetRequest,
): Result<Message> {
	val subject =
		buildString {
			when {
				context.directGet && req.lastFor != null -> {
					append(context.config.apiPrefix)
					append(DIRECT_GET)
					append(streamName)
					append(".")
					append(req.lastFor)
				}
				context.directGet -> {
					append(context.config.apiPrefix)
					append(DIRECT_GET)
					append(streamName)
				}
				else -> {
					append(context.config.apiPrefix)
					append(MSG_GET)
					append(streamName)
				}
			}
		}

	val payload =
		when {
			context.directGet && req.lastFor != null -> null
			else -> wireJsonFormat.encodeToString(req)
		}

	val response = request(subject, payload)

	return Result.success(response)
}

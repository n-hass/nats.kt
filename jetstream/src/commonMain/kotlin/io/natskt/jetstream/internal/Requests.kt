package io.natskt.jetstream.internal

import io.natskt.api.Message
import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.AccountInfo
import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.ConsumerConfig
import io.natskt.jetstream.api.ConsumerDeleteResponse
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.ConsumerListResponse
import io.natskt.jetstream.api.ConsumerNamesResponse
import io.natskt.jetstream.api.ConsumerPauseRequest
import io.natskt.jetstream.api.ConsumerPauseResponse
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.JetStreamUnknownResponseException
import io.natskt.jetstream.api.MessageDeleteRequest
import io.natskt.jetstream.api.MessageDeleteResponse
import io.natskt.jetstream.api.MessageGetRequest
import io.natskt.jetstream.api.MessageInfoResponse
import io.natskt.jetstream.api.PurgeOptions
import io.natskt.jetstream.api.PurgeResponse
import io.natskt.jetstream.api.StoredMessage
import io.natskt.jetstream.api.StreamConfig
import io.natskt.jetstream.api.StreamDeleteResponse
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.StreamInfoOptions
import io.natskt.jetstream.api.StreamListResponse
import io.natskt.jetstream.api.StreamNamesResponse
import io.natskt.jetstream.api.consumer.ConsumerCreateAction
import io.natskt.jetstream.api.consumer.ConsumerCreateRequest
import io.natskt.jetstream.api.internal.decode
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal const val STREAM_INFO = "STREAM.INFO."
internal const val STREAM_CREATE = "STREAM.CREATE."
internal const val STREAM_UPDATE = "STREAM.UPDATE."
internal const val STREAM_DELETE = "STREAM.DELETE."
internal const val STREAM_PURGE = "STREAM.PURGE."
internal const val STREAM_LIST = "STREAM.LIST"
internal const val STREAM_NAMES = "STREAM.NAMES"
internal const val CONSUMER_CREATE = "CONSUMER.CREATE."
internal const val CONSUMER_INFO = "CONSUMER.INFO."
internal const val CONSUMER_DELETE = "CONSUMER.DELETE."
internal const val CONSUMER_PAUSE = "CONSUMER.PAUSE."
internal const val CONSUMER_LIST = "CONSUMER.LIST."
internal const val CONSUMER_NAMES = "CONSUMER.NAMES."
internal const val MSG_GET = "STREAM.MSG.GET."
internal const val MSG_DIRECT_GET = "DIRECT.GET."
internal const val MSG_DELETE = "STREAM.MSG.DELETE."

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

	return when (val data = request(subject, null).decode<ConsumerDeleteResponse>()) {
		is ConsumerDeleteResponse ->
			if (data.success) {
				Result.success(Unit)
			} else {
				Result.failure(JetStreamApiException(ApiError(description = "Delete failed")))
			}
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
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
					append(MSG_DIRECT_GET)
					append(streamName)
					append(".")
					append(req.lastFor)
				}
				context.directGet -> {
					append(context.config.apiPrefix)
					append(MSG_DIRECT_GET)
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

internal suspend fun CanRequest.updateStream(configuration: StreamConfig): Result<StreamInfo> =
	when (val data = request(context.config.apiPrefix + STREAM_UPDATE + configuration.name, wireJsonFormat.encodeToString(configuration)).decode<StreamInfo>()) {
		is StreamInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}

internal suspend fun CanRequest.deleteStream(streamName: String): Result<Boolean> {
	val subject = context.config.apiPrefix + STREAM_DELETE + streamName
	return when (val data = request(subject, null).decode<StreamDeleteResponse>()) {
		is StreamDeleteResponse -> Result.success(data.success)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.getStreamInfo(
	name: String,
	options: StreamInfoOptions?,
): Result<StreamInfo> {
	val payload = options?.let { wireJsonFormat.encodeToString(it) }
	return when (val data = request(context.config.apiPrefix + STREAM_INFO + name, payload).decode<StreamInfo>()) {
		is StreamInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.purgeStream(
	streamName: String,
	options: PurgeOptions?,
): Result<PurgeResponse> {
	val payload = options?.let { wireJsonFormat.encodeToString(it) }
	return when (val data = request(context.config.apiPrefix + STREAM_PURGE + streamName, payload).decode<PurgeResponse>()) {
		is PurgeResponse -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

@Serializable
private data class StreamNamesRequest(
	val subject: String? = null,
)

internal suspend fun CanRequest.getStreamNames(subjectFilter: String?): Result<List<String>> {
	val payload = subjectFilter?.let { wireJsonFormat.encodeToString(StreamNamesRequest(it)) }
	return when (val data = request(context.config.apiPrefix + STREAM_NAMES, payload).decode<StreamNamesResponse>()) {
		is StreamNamesResponse -> Result.success(data.streams)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.getStreams(subjectFilter: String?): Result<List<StreamInfo>> {
	val payload = subjectFilter?.let { wireJsonFormat.encodeToString(StreamNamesRequest(it)) }
	return when (val data = request(context.config.apiPrefix + STREAM_LIST, payload).decode<StreamListResponse>()) {
		is StreamListResponse -> Result.success(data.streams)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.createConsumer(
	streamName: String,
	configuration: ConsumerConfig,
): Result<ConsumerInfo> {
	val consumerName = configuration.durableName ?: throw IllegalArgumentException("Consumer name is required")

	val subject =
		if (configuration.filterSubject != null && configuration.filterSubjects.isNullOrEmpty()) {
			context.config.apiPrefix + CONSUMER_CREATE + streamName + "." + consumerName + "." + configuration.filterSubject
		} else {
			context.config.apiPrefix + CONSUMER_CREATE + streamName + "." + consumerName
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

internal suspend fun CanRequest.updateConsumer(
	streamName: String,
	configuration: ConsumerConfig,
): Result<ConsumerInfo> {
	val consumerName = configuration.durableName ?: throw IllegalArgumentException("Consumer name is required")

	val subject =
		if (configuration.filterSubject != null && configuration.filterSubjects.isNullOrEmpty()) {
			context.config.apiPrefix + CONSUMER_CREATE + streamName + "." + consumerName + "." + configuration.filterSubject
		} else {
			context.config.apiPrefix + CONSUMER_CREATE + streamName + "." + consumerName
		}

	val payload =
		wireJsonFormat.encodeToString(
			ConsumerCreateRequest(
				streamName = streamName,
				config = configuration,
				action = ConsumerCreateAction.Update,
			),
		)

	return when (val data = request(subject, payload).decode<ConsumerInfo>()) {
		is ConsumerInfo -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.pauseConsumer(
	streamName: String,
	consumerName: String,
	pauseUntil: Instant,
): Result<ConsumerPauseResponse> {
	val subject = context.config.apiPrefix + CONSUMER_PAUSE + streamName + "." + consumerName
	val payload = wireJsonFormat.encodeToString(ConsumerPauseRequest(pauseUntil))
	return when (val data = request(subject, payload).decode<ConsumerPauseResponse>()) {
		is ConsumerPauseResponse -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.resumeConsumer(
	streamName: String,
	consumerName: String,
): Result<Boolean> {
	// Resuming is pausing until null
	val subject = context.config.apiPrefix + CONSUMER_PAUSE + streamName + "." + consumerName
	val payload = wireJsonFormat.encodeToString(ConsumerPauseRequest(null))
	return when (val data = request(subject, payload).decode<ConsumerPauseResponse>()) {
		is ConsumerPauseResponse -> Result.success(!data.paused)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.getConsumerNames(streamName: String): Result<List<String>> {
	val subject = context.config.apiPrefix + CONSUMER_NAMES + streamName
	return when (val data = request(subject, null).decode<ConsumerNamesResponse>()) {
		is ConsumerNamesResponse -> Result.success(data.consumers)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.getConsumers(streamName: String): Result<List<ConsumerInfo>> {
	val subject = context.config.apiPrefix + CONSUMER_LIST + streamName
	return when (val data = request(subject, null).decode<ConsumerListResponse>()) {
		is ConsumerListResponse -> Result.success(data.consumers)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.deleteMessage(
	streamName: String,
	sequence: ULong,
	erase: Boolean,
): Result<Boolean> {
	val subject = context.config.apiPrefix + MSG_DELETE + streamName
	val payload = wireJsonFormat.encodeToString(MessageDeleteRequest(sequence, !erase))
	return when (val data = request(subject, payload).decode<MessageDeleteResponse>()) {
		is MessageDeleteResponse -> Result.success(data.success)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.getMessageInfo(
	streamName: String,
	req: MessageGetRequest,
): Result<MessageInfoResponse> {
	val subject =
		buildString {
			append(context.config.apiPrefix)
			append(MSG_GET)
			append(streamName)
		}

	val payload = wireJsonFormat.encodeToString(req)

	val response = request(subject, payload)

	return when (val data = response.decode<MessageInfoResponse>()) {
		is MessageInfoResponse -> Result.success(data)
		is ApiError -> Result.failure(JetStreamApiException(data))
		else -> Result.failure(JetStreamUnknownResponseException(data))
	}
}

internal suspend fun CanRequest.getMessageDirect(
	streamName: String,
	req: MessageGetRequest,
): Result<StoredMessage> {
	val subject =
		buildString {
			when {
				req.lastFor != null -> {
					append(context.config.apiPrefix)
					append(MSG_DIRECT_GET)
					append(streamName)
					append(".")
					append(req.lastFor)
				}
				else -> {
					append(context.config.apiPrefix)
					append(MSG_DIRECT_GET)
					append(streamName)
				}
			}
		}

	val payload =
		when {
			req.lastFor != null -> null
			else -> wireJsonFormat.encodeToString(req)
		}

	val response = request(subject, payload)

	return convertDirectGetMsgToMessageInfo(response)
}

private fun convertDirectGetMsgToMessageInfo(msg: Message): Result<StoredMessage> {
	val data = msg.data
	if (data == null || data.isEmpty()) {
		if (msg.status == 404 || msg.status == 408) {
			return Result.failure(JetStreamApiException(ApiError(errCode = msg.status, description = "message not found")))
		}
	}

	val headers =
		msg.headers?.toMutableMap() ?: return Result.failure(
			IllegalStateException("Direct-get response missing headers"),
		)

	val subject =
		headers.remove("Nats-Subject")?.firstOrNull()
			?: return Result.failure(IllegalStateException("Direct-get response missing Nats-Subject header"))

	val seqStr =
		headers.remove("Nats-Sequence")?.firstOrNull()
			?: return Result.failure(IllegalStateException("Direct-get response missing Nats-Sequence header"))

	val sequence =
		seqStr.toULongOrNull()
			?: return Result.failure(IllegalStateException("Invalid Nats-Sequence header: $seqStr"))

	val timeStr =
		headers.remove("Nats-Time-Stamp")?.firstOrNull()
			?: return Result.failure(IllegalStateException("Direct-get response missing Nats-Time-Stamp header"))

	val storedMessage =
		StoredMessage(
			subject = subject,
			sequence = sequence,
			data = data,
			headers = headers,
			time = timeStr,
		)

	return Result.success(storedMessage)
}

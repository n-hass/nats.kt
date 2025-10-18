package io.natskt.jetstream.internal

import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.JetStreamUnknownResponseException
import io.natskt.jetstream.api.StreamConfiguration
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.internal.decodeApiResponse
import io.natskt.jetstream.client.JetStreamClientImpl

internal const val STREAM_INFO = "STREAM.INFO."
internal const val STREAM_CREATE = "STREAM.CREATE."

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

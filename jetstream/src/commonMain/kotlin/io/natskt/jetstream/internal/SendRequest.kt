package io.natskt.jetstream.internal

import io.natskt.api.NatsClient
import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.ApiResponse
import io.natskt.jetstream.api.JetStreamApiResponse
import io.natskt.jetstream.api.internal.decodeApiResponse

internal suspend inline fun <reified T : JetStreamApiResponse> NatsClient.jsRequest(
	subject: String,
	message: String?,
	headers: Map<String, List<String>>? = null,
	timeoutMs: Long = 5000,
): ApiResponse {
	val msg = this.request(subject, message?.encodeToByteArray(), headers)
	if (msg.data == null || msg.data!!.isEmpty()) {
		return ApiError(code = msg.status)
	}
	return wireJsonFormat.decodeApiResponse<T>(msg.data!!.decodeToString())
}

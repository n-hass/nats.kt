package io.natskt.jetstream.api.internal

import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.ApiResponse
import io.natskt.jetstream.api.JetStreamApiResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

internal inline fun <reified T : JetStreamApiResponse> Json.decodeApiResponse(string: String): ApiResponse {
	val element = parseToJsonElement(string)
	return if (element is JsonObject &&
		!(element["error"] as? JsonObject).isNullOrEmpty()
	) {
		decodeFromJsonElement<ApiError>(element["error"]!!)
	} else {
		decodeFromJsonElement<T>(element)
	}
}

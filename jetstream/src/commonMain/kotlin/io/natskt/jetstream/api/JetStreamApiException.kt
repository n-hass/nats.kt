package io.natskt.jetstream.api

public class JetStreamApiException(
	public val error: ApiError?,
	override val message: String? = "Server returned an error: $error",
) : Exception(message)

public class JetStreamUnknownResponseException(
	public val response: ApiResponse,
) : Exception("An unknown response was received: ${response::class.simpleName}")

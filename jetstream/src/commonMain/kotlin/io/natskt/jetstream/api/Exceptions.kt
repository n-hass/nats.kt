package io.natskt.jetstream.api

import io.natskt.api.NatsClientException

public class JetStreamException(
	message: String? = null,
	cause: Throwable? = null,
) : NatsClientException(message, cause)

public class JetStreamApiException(
	public val error: ApiError?,
	override val message: String? = "Server returned an error: $error",
) : NatsClientException(message)

public class JetStreamUnknownResponseException(
	public val response: ApiResponse,
) : NatsClientException("An unknown response was received: $response")

package io.natskt.jetstream.api

import io.natskt.api.NatsClientException

public open class JetStreamException(
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

/**
 * Sealed parent for non-200 status responses received on a pull-consume reply
 * subject. The server may send 404/408 (which the client treats as benign
 * end-of-window signals and does NOT raise) or 409/503 (which raise the
 * concrete subclasses below).
 */
public sealed class JetStreamPullStatusException(
	public val code: Int,
	public val description: String?,
) : JetStreamException(
		message = description?.let { "$code $it" } ?: code.toString(),
	)

/** 409 — consumer state issue (deleted, paused, leadership change, etc.). */
public class JetStreamConsumerStateException(
	code: Int,
	description: String?,
) : JetStreamPullStatusException(code, description)

/** 503 — no responders / server unavailable for this consumer. */
public class JetStreamConnectivityException(
	code: Int,
	description: String?,
) : JetStreamPullStatusException(code, description)

/**
 * Synthetic — raised when the consumer's idle-heartbeat watchdog observes that
 * two or more heartbeat intervals elapsed without any inbound traffic.
 */
public class JetStreamHeartbeatLostException(
	description: String? = null,
) : JetStreamPullStatusException(code = 100, description = description)

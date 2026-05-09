package io.natskt.jetstream.internal

import io.natskt.api.Message
import io.natskt.api.NatsClient

internal const val HEARTBEAT_STATUS: Int = 100
internal const val BAD_REQUEST_STATUS: Int = 400
internal const val NO_MESSAGES_STATUS: Int = 404
internal const val REQUEST_TIMEOUT_STATUS: Int = 408
internal const val CONFLICT_STATUS: Int = 409
internal const val REQUIRED_API_LEVEL_STATUS: Int = 412
internal const val PIN_ID_MISMATCH_STATUS: Int = 423
internal const val NO_RESPONDERS_STATUS: Int = 503

internal const val DESC_EXCEEDS_MAXBYTES: String = "exceeds maxbytes"

internal const val STALLED_HEADER: String = "Nats-Consumer-Stalled"

/**
 * Push-consumer-only: reply to a `100 FlowControl Request` so the server's
 * flow-control handshake completes. The server expects an empty publish either
 * to the message's [Message.replyTo] (for FlowControl) or to the subject named
 * in the `Nats-Consumer-Stalled` header.
 *
 * Pull consumers must NOT call this — pull-side `100 Idle Heartbeat` is a
 * watchdog signal only, never replied to.
 */
internal suspend fun replyToHeartbeatOrFlowControl(
	client: NatsClient,
	message: Message,
) {
	val replyTo = message.replyTo
	if (replyTo != null) {
		client.publish(replyTo.raw, null)
		return
	}
	message.headers?.get(STALLED_HEADER)?.firstOrNull()?.let { stalledSubject ->
		client.publish(stalledSubject, null)
	}
}

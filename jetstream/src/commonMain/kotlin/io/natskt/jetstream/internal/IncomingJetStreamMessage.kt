@file:OptIn(InternalNatsApi::class, ExperimentalTime::class)

package io.natskt.jetstream.internal

import io.natskt.api.JetStreamMessage
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.JetStreamMessageInternal
import io.natskt.internal.MessageInternal
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal val ACK_ACK = "+ACK".encodeToByteArray()
internal val ACK_NAK = "-NAK".encodeToByteArray()
internal val ACK_PROGRESS = "+WPI".encodeToByteArray()
internal val ACK_TERM = "+TERM".encodeToByteArray()

internal data class IncomingJetStreamMessage(
	private val original: MessageInternal,
	private val client: NatsClient,
) : JetStreamMessageInternal,
	MessageInternal by original {
	override suspend fun ack() {
		if (original.replyTo != null) {
			client.publish(original.replyTo!!.raw, ACK_ACK)
		}
	}

	override suspend fun ackSync() {
		if (original.replyTo != null) {
			client.request(original.replyTo!!.raw, ACK_ACK)
		}
	}

	override suspend fun nak() {
		if (original.replyTo != null) {
			client.publish(original.replyTo!!.raw, ACK_NAK)
		}
	}

	override suspend fun inProgress() {
		if (original.replyTo != null) {
			client.publish(original.replyTo!!.raw, ACK_PROGRESS)
		}
	}

	override suspend fun term() {
		if (original.replyTo != null) {
			client.publish(original.replyTo!!.raw, ACK_TERM)
		}
	}

	override val metadata by lazy {
		parseAckMetadata(original.replyTo)
	}
}

private const val ACK_V1_TOKEN_COUNT = 9
private const val ACK_V2_MIN_TOKEN_COUNT = 11
private const val ACK_DOMAIN_TOKEN_POS = 2
private const val ACK_STREAM_SEQ_TOKEN_POS = 7
private const val ACK_TIMESTAMP_TOKEN_POS = 9
private const val ACK_PENDING_TOKEN_POS = 10

internal fun parseAckMetadata(reply: Subject?): JetStreamMessage.Metadata? {
	val raw = reply?.raw ?: return null
	val tokens = raw.split('.').toMutableList()
	if (tokens.size < ACK_V1_TOKEN_COUNT) return null
	if (tokens.size > ACK_V1_TOKEN_COUNT && tokens.size < ACK_V2_MIN_TOKEN_COUNT) return null
	if (tokens[0] != "\$JS" || tokens[1] != "ACK") return null

	if (tokens.size == ACK_V1_TOKEN_COUNT) {
		tokens.add(ACK_DOMAIN_TOKEN_POS, "")
		tokens.add(ACK_DOMAIN_TOKEN_POS + 1, "")
	} else if (tokens[ACK_DOMAIN_TOKEN_POS] == "_") {
		tokens[ACK_DOMAIN_TOKEN_POS] = ""
	}

	val streamSeq = tokens.getOrNull(ACK_STREAM_SEQ_TOKEN_POS)?.toULongOrNull() ?: return null
	val pending = tokens.getOrNull(ACK_PENDING_TOKEN_POS)?.toULongOrNull() ?: 0u
	val timestamp = tokens.getOrNull(ACK_TIMESTAMP_TOKEN_POS)?.toLongOrNull() ?: 0L
	return JetStreamMessage.Metadata(streamSeq, pending, ackTimestampToInstant(timestamp))
}

private fun ackTimestampToInstant(nanos: Long): Instant {
	if (nanos <= 0) return Instant.DISTANT_PAST
	val millis = nanos / 1_000_000
	val remainder = nanos % 1_000_000
	return Instant.fromEpochMilliseconds(millis).plus(remainder.nanoseconds)
}

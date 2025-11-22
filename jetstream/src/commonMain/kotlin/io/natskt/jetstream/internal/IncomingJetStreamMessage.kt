@file:OptIn(InternalNatsApi::class)

package io.natskt.jetstream.internal

import io.natskt.api.NatsClient
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.JetStreamMessageInternal
import io.natskt.internal.MessageInternal

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
}

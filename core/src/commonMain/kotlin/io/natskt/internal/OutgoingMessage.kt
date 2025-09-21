package io.natskt.internal

import io.natskt.api.Message

internal data class OutgoingMessage(
	override val subject: Subject,
	override val replyTo: Subject?,
	override val headers: Map<String, List<String>>?,
	override val data: ByteArray?,
) : Message {
	override val ack: (() -> Unit)? = null
	override val ackWait: (suspend () -> Unit)? = null
	override val nak: (() -> Unit)? = null

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as OutgoingMessage

		if (subject != other.subject) return false
		if (replyTo != other.replyTo) return false
		if (headers != other.headers) return false
		if (!data.contentEquals(other.data)) return false
		if (ack != other.ack) return false
		if (ackWait != other.ackWait) return false
		if (nak != other.nak) return false

		return true
	}

	override fun hashCode(): Int {
		var result = subject.hashCode()
		result = 31 * result + replyTo.hashCode()
		result = 31 * result + headers.hashCode()
		result = 31 * result + data.contentHashCode()
		result = 31 * result + (ack?.hashCode() ?: 0)
		result = 31 * result + (ackWait?.hashCode() ?: 0)
		result = 31 * result + (nak?.hashCode() ?: 0)
		return result
	}
}

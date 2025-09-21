package io.natskt.internal

import io.natskt.api.Message

internal data class IncomingCoreMessage(
	override val subject: Subject,
	override val replyTo: Subject?,
	override val headers: Map<String, List<String>>?,
	override val data: ByteArray?,
) : Message {
	override val ack = null
	override val ackWait = null
	override val nak = null

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as IncomingCoreMessage

		if (subject != other.subject) return false
		if (replyTo != other.replyTo) return false
		if (headers != other.headers) return false
		if (!data.contentEquals(other.data)) return false

		return true
	}

	override fun hashCode(): Int {
		var result = subject.hashCode()
		result = 31 * result + (replyTo?.hashCode() ?: 0)
		result = 31 * result + (headers?.hashCode() ?: 0)
		result = 31 * result + (data?.contentHashCode() ?: 0)
		result = 31 * result + (ack?.hashCode() ?: 0)
		result = 31 * result + (ackWait?.hashCode() ?: 0)
		result = 31 * result + (nak?.hashCode() ?: 0)
		return result
	}
}

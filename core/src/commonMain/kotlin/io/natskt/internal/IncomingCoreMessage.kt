package io.natskt.internal

import io.natskt.api.internal.MessageInternal

internal data class IncomingCoreMessage(
	override val sid: String,
	val subjectString: String,
	val replyToString: String?,
	override val data: ByteArray?,
	override val headers: Map<String, List<String>>?,
) : MessageInternal {
	override var ackWait: (suspend () -> Unit)? = null
	override var ack: (() -> Unit)? = null
	override var nak: (() -> Unit)? = null

	override val subject by lazy {
		Subject(subjectString)
	}

	override val replyTo by lazy {
		if (replyToString == null) return@lazy null
		Subject(replyToString)
	}

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

@file:OptIn(InternalNatsApi::class)

package io.natskt.jetstream.internal

import io.natskt.api.AckAction
import io.natskt.api.Subject
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.JetStreamMessageInternal
import io.natskt.jetstream.api.JetStreamMessageMetadata

internal data class IncomingJetStreamMessage(
	override val sid: String,
	val subjectString: String,
	val replyToString: String?,
	override val headers: Map<String, List<String>>?,
	override val data: ByteArray?,
	override val ack: AckAction,
	override val nak: AckAction,
	val metadata: JetStreamMessageMetadata,
	override val status: Int?,
) : JetStreamMessageInternal {
	override val subject by lazy {
		Subject(subjectString)
	}

	override val replyTo by lazy {
		if (replyToString == null) return@lazy null
		Subject(replyToString)
	}

	override val ackWait: AckAction = { ack() }

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as IncomingJetStreamMessage

		if (status != other.status) return false
		if (sid != other.sid) return false
		if (subjectString != other.subjectString) return false
		if (replyToString != other.replyToString) return false
		if (headers != other.headers) return false
		if (!data.contentEquals(other.data)) return false
		if (ack != other.ack) return false
		if (nak != other.nak) return false
		if (metadata != other.metadata) return false
		if (ackWait != other.ackWait) return false
		if (subject != other.subject) return false
		if (replyTo != other.replyTo) return false

		return true
	}

	override fun hashCode(): Int {
		var result = status ?: 0
		result = 31 * result + sid.hashCode()
		result = 31 * result + subjectString.hashCode()
		result = 31 * result + (replyToString?.hashCode() ?: 0)
		result = 31 * result + (headers?.hashCode() ?: 0)
		result = 31 * result + (data?.contentHashCode() ?: 0)
		result = 31 * result + ack.hashCode()
		result = 31 * result + nak.hashCode()
		result = 31 * result + metadata.hashCode()
		result = 31 * result + ackWait.hashCode()
		result = 31 * result + subject.hashCode()
		result = 31 * result + (replyTo?.hashCode() ?: 0)
		return result
	}
}

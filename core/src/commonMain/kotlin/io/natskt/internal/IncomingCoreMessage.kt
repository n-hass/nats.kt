@file:OptIn(InternalNatsApi::class)

package io.natskt.internal

import io.natskt.api.Subject
import io.natskt.api.internal.InternalNatsApi

internal data class IncomingCoreMessage(
	override val sid: String,
	val subjectString: String,
	val replyToString: String?,
	override val data: ByteArray?,
	override val headers: Map<String, List<String>>?,
	override val status: Int?,
) : MessageInternal {
	override val subject: Subject by lazy {
		Subject(subjectString)
	}

	override val replyTo: Subject? by lazy {
		if (replyToString == null) return@lazy null
		Subject(replyToString)
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as IncomingCoreMessage

		if (status != other.status) return false
		if (sid != other.sid) return false
		if (subjectString != other.subjectString) return false
		if (replyToString != other.replyToString) return false
		if (!data.contentEquals(other.data)) return false
		if (headers != other.headers) return false
		if (subject != other.subject) return false
		if (replyTo != other.replyTo) return false

		return true
	}

	override fun hashCode(): Int {
		var result = status ?: 0
		result = 31 * result + sid.hashCode()
		result = 31 * result + subjectString.hashCode()
		result = 31 * result + (replyToString?.hashCode() ?: 0)
		result = 31 * result + (data?.contentHashCode() ?: 0)
		result = 31 * result + (headers?.hashCode() ?: 0)
		result = 31 * result + subject.hashCode()
		result = 31 * result + (replyTo?.hashCode() ?: 0)
		return result
	}
}

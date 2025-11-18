@file:OptIn(ExperimentalSerializationApi::class)

package io.natskt.internal

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface ClientOperation : Operation {
	@Serializable
	data class ConnectOp(
		val verbose: Boolean,
		val pedantic: Boolean,
		@SerialName("tls_required")
		val tlsRequired: Boolean,
		@SerialName("auth_token")
		val authToken: String?,
		val user: String?,
		val pass: String?,
		val name: String?,
		@EncodeDefault
		val lang: String = "Kotlin",
		@EncodeDefault
		val version: String = CLIENT_VERSION,
		val protocol: Int = 1,
		val echo: Boolean = false,
		val sig: String?,
		val jwt: String?,
		@SerialName("no_responders")
		val noResponders: Boolean?,
		@EncodeDefault
		val headers: Boolean? = true,
		val nkey: String?,
	) : ClientOperation

	@Serializable
	data class PubOp(
		val subject: String,
		@SerialName("reply-to")
		val replyTo: String?,
		val payload: ByteArray?,
	) : ClientOperation {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other == null || this::class != other::class) return false

			other as PubOp

			if (subject != other.subject) return false
			if (replyTo != other.replyTo) return false
			if (!payload.contentEquals(other.payload)) return false

			return true
		}

		override fun hashCode(): Int {
			var result = subject.hashCode()
			result = 31 * result + (replyTo?.hashCode() ?: 0)
			result = 31 * result + (payload?.contentHashCode() ?: 0)
			return result
		}
	}

	@Serializable
	data class HPubOp(
		val subject: String,
		@SerialName("reply-to")
		val replyTo: String?,
		val headers: Map<String, List<String>>?,
		val payload: ByteArray?,
	) : ClientOperation {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other == null || this::class != other::class) return false

			other as HPubOp

			if (subject != other.subject) return false
			if (replyTo != other.replyTo) return false
			if (headers != other.headers) return false
			if (!payload.contentEquals(other.payload)) return false

			return true
		}

		override fun hashCode(): Int {
			var result = subject.hashCode()
			result = 31 * result + (replyTo?.hashCode() ?: 0)
			result = 31 * result + (headers?.hashCode() ?: 0)
			result = 31 * result + (payload?.contentHashCode() ?: 0)
			return result
		}
	}

	@Serializable
	data class SubOp(
		val subject: String,
		val queueGroup: String?,
		val sid: String,
	) : ClientOperation

	@Serializable
	data class UnSubOp(
		val sid: String,
		@SerialName("max_msgs")
		val maxMsgs: Int?,
	) : ClientOperation
}

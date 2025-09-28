package io.natskt.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface ServerOperation : Operation {
	@Serializable
	data class InfoOp(
		@SerialName("server_id")
		val serverId: String,
		@SerialName("server_name")
		val serverName: String,
		val version: String,
		val go: String,
		val host: String,
		val port: Int,
		val headers: Boolean,
		@SerialName("max_payload")
		val maxPayload: Int,
		val proto: Int,
		@SerialName("client_id")
		val clientId: Long?,
		@SerialName("auth_required")
		val authRequired: Boolean?,
		@SerialName("tls_required")
		val tlsRequired: Boolean?,
		@SerialName("tls_verify")
		val tlsVerify: Boolean?,
		@SerialName("tls_available")
		val tlsAvailable: Boolean?,
		@SerialName("connect_urls")
		val connectUrls: List<String>?,
		@SerialName("ws_connect_urls")
		val wsConnectUrls: List<String>?,
		val ldm: Boolean?,
		@SerialName("git_commit")
		val gitCommit: String?,
		val jetstream: Boolean?,
		val ip: String?,
		@SerialName("client_ip")
		val clientIp: String?,
		val nonce: String?,
		val cluster: String?,
		val domain: String?,
		val xkey: String?,
	) : ServerOperation

	@Serializable
	data class MsgOp(
		val subject: String,
		val sid: String,
		@SerialName("reply-to")
		val replyTo: String?,
		val bytes: Int,
		val payload: ByteArray?,
	) : ServerOperation {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other == null || this::class != other::class) return false

			other as MsgOp

			if (bytes != other.bytes) return false
			if (subject != other.subject) return false
			if (sid != other.sid) return false
			if (replyTo != other.replyTo) return false
			if (!payload.contentEquals(other.payload)) return false

			return true
		}

		override fun hashCode(): Int {
			var result = bytes
			result = 31 * result + subject.hashCode()
			result = 31 * result + sid.hashCode()
			result = 31 * result + (replyTo?.hashCode() ?: 0)
			result = 31 * result + (payload?.contentHashCode() ?: 0)
			return result
		}
	}

	@Serializable
	data class HMsgOp(
		val subject: String,
		val sid: String,
		@SerialName("reply-to")
		val replyTo: String?,
		val headerBytes: Int,
		val totalBytes: Int,
		val headers: Map<String, List<String>>?,
		val payload: ByteArray?,
	) : ServerOperation {
		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (other == null || this::class != other::class) return false

			other as HMsgOp

			if (headerBytes != other.headerBytes) return false
			if (totalBytes != other.totalBytes) return false
			if (subject != other.subject) return false
			if (sid != other.sid) return false
			if (replyTo != other.replyTo) return false
			if (headers != other.headers) return false
			if (!payload.contentEquals(other.payload)) return false

			return true
		}

		override fun hashCode(): Int {
			var result = headerBytes
			result = 31 * result + totalBytes
			result = 31 * result + subject.hashCode()
			result = 31 * result + sid.hashCode()
			result = 31 * result + (replyTo?.hashCode() ?: 0)
			result = 31 * result + (headers?.hashCode() ?: 0)
			result = 31 * result + (payload?.contentHashCode() ?: 0)
			return result
		}
	}
}

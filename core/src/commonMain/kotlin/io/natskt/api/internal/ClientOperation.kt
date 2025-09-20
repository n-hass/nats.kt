@file:OptIn(ExperimentalSerializationApi::class)

package io.natskt.api.internal

import io.natskt.internal.BuildKonfig
import io.natskt.internal.Subject
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal sealed interface ClientOperation : Operation {
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
		val version: String = BuildKonfig.version,
		val protocol: Int?,
		val echo: Boolean,
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
	) : ClientOperation

	@Serializable
	data class HPubOp(
		val subject: String,
		@SerialName("reply-to")
		val replyTo: String?,
		val headers: Map<String, List<String>>?,
		val payload: ByteArray?,
	) : ClientOperation

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

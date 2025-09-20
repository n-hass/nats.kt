@file:OptIn(ExperimentalSerializationApi::class)

package io.natskt.api.internal

import io.natskt.internal.BuildKonfig
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
		val headers: Boolean? = true,
		val nkey: String?,
	) : ClientOperation
}

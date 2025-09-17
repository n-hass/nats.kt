@file:OptIn(ExperimentalSerializationApi::class)

package io.natskt.internal.api

import io.natskt.internal.BuildKonfig
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal data object Ok : Operation
internal data object Ack : Operation
internal data object Ping : Operation
internal data object Pong : Operation

@Serializable
internal data class InfoOp(
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
internal data class ConnectOp(
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
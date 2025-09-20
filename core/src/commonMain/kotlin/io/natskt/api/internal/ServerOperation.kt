package io.natskt.api.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal sealed interface ServerOperation : Operation {
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
}

package harness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class RemoteNatsServerRequest(
	@SerialName("enable_jetstream")
	val enableJetStream: Boolean = true,
	@SerialName("enable_tls")
	val enableTls: Boolean = false,
	@SerialName("tls_handshake_first")
	val tlsHandshakeFirst: Boolean = false,
	@SerialName("tls_require_client_cert")
	val tlsRequireClientCert: Boolean = false,
)

@Serializable
public data class RemoteNatsServerInfo(
	val id: String,
	@SerialName("tcp_uri")
	val tcpUri: String,
	@SerialName("websocket_uri")
	val websocketUri: String,
	@SerialName("tls_uri")
	val tlsUri: String? = null,
	/** PEM-encoded server CA certificate. Tests pin it as a trust anchor. */
	@SerialName("tls_server_cert_pem")
	val tlsServerCertPem: String? = null,
	/** PEM-encoded client certificate signed by the harness's client CA. Present when [RemoteNatsServerRequest.tlsRequireClientCert] is true. */
	@SerialName("tls_client_cert_pem")
	val tlsClientCertPem: String? = null,
	/** PKCS#8 PEM-encoded client private key matching [tlsClientCertPem]. */
	@SerialName("tls_client_key_pem")
	val tlsClientKeyPem: String? = null,
)

@Serializable
public data class RemoteNatsServerLogs(
	@SerialName("next_offset")
	val nextOffset: Int,
	val entries: List<String> = emptyList(),
)

public const val DEFAULT_REMOTE_HARNESS_URL: String = "http://127.0.0.1:4500"

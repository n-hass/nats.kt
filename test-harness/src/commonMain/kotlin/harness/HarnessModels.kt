package harness

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class RemoteNatsServerRequest(
	@SerialName("enable_jetstream")
	val enableJetStream: Boolean = true,
)

@Serializable
public data class RemoteNatsServerInfo(
	val id: String,
	@SerialName("tcp_uri")
	val tcpUri: String,
	@SerialName("websocket_uri")
	val websocketUri: String,
)

@Serializable
public data class RemoteNatsServerLogs(
	@SerialName("next_offset")
	val nextOffset: Int,
	val entries: List<String> = emptyList(),
)

public const val DEFAULT_REMOTE_HARNESS_URL: String = "http://127.0.0.1:4500"

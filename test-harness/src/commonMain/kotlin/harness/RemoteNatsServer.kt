package harness

public class RemoteNatsServer internal constructor(
	private val client: RemoteNatsHarnessClient,
	private val info: RemoteNatsServerInfo,
) {
	private var closed = false

	public val tcpUri: String
		get() = info.tcpUri

	public val websocketUri: String
		get() = info.websocketUri

	public val uri: String
		get() = uriFor(platformHarnessTransport)

	public fun uriFor(transport: RemoteNatsServerTransport): String =
		when (transport) {
			RemoteNatsServerTransport.Tcp -> tcpUri
			RemoteNatsServerTransport.WebSocket -> websocketUri
		}

	internal suspend fun fetchLogs(from: Int): RemoteNatsServerLogs = client.fetchLogs(info.id, from)

	public suspend fun logSnapshot(): List<String> = client.fetchLogs(info.id, 0).entries

	public suspend fun closeAsync() {
		if (closed) return
		closed = true
		runCatching { client.deleteServer(info.id) }
	}
}

package harness

public class RemoteNatsServer internal constructor(
	private val client: RemoteNatsHarnessClient,
	private val info: RemoteNatsServerInfo,
) {
	private var closed = false

	public val uri: String
		get() = info.uri

	internal suspend fun fetchLogs(from: Int): RemoteNatsServerLogs = client.fetchLogs(info.id, from)

	public suspend fun logSnapshot(): List<String> = client.fetchLogs(info.id, 0).entries

	public suspend fun closeAsync() {
		if (closed) return
		closed = true
		runCatching { client.deleteServer(info.id) }
	}
}

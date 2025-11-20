package harness

public enum class RemoteNatsServerTransport {
	Tcp,
	WebSocket,
}

public expect val platformHarnessTransport: RemoteNatsServerTransport

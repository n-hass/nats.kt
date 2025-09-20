package io.natskt.api

public enum class ConnectionPhase {
	Idle,
	Connecting,
	Connected,
	Draining,
	LameDuck,
	Closing,
	Closed,
	Failed,
}

public data class ConnectionState(
	var phase: ConnectionPhase,
	var rtt: Double?,
	var lastPingAt: Long?,
	var lastPongAt: Long?,
	var messagesIn: ULong,
	var messagesOut: ULong,
) {
	internal companion object {
		val Uninitialised =
			ConnectionState(
				phase = ConnectionPhase.Idle,
				rtt = null,
				lastPingAt = null,
				lastPongAt = null,
				messagesIn = 0u,
				messagesOut = 0u,
			)
	}
}

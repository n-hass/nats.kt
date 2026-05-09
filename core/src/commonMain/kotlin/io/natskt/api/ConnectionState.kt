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
	/**
	 * Reason text from the most recent `-ERR` operation sent by the server.
	 *
	 * Updated on every `-ERR`; persists across reconnects (latest wins) so a closed
	 * connection's failure reason can be correlated with its cause (auth violations,
	 * permissions violations, slow-consumer kicks, max-payload violations, etc.).
	 *
	 * `null` until the first `-ERR` is received.
	 */
	var lastError: String? = null,
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
				lastError = null,
			)
	}
}

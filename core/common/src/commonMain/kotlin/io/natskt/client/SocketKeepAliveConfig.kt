package io.natskt.client

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configures TCP `SO_KEEPALIVE` on the client's underlying socket so half-open connections
 * (peer crashed, NAT/firewall silently dropped the flow) are detected within roughly
 * `idle + interval * probeCount` instead of the OS default (typically two hours).
 *
 * Set on [ClientConfigurationBuilder.socketKeepAlive]; the client applies it to the raw TCP socket
 * before any TLS upgrade, so both plain and TLS connections honour the same configuration.
 *
 * Platform support:
 * - **Kotlin/Native** (linux, apple): full tuning via `setsockopt`.
 * - **JVM**: `SO_KEEPALIVE` is enabled via Ktor; per-socket idle/interval/probe tuning depends
 *   on the JDK and OS. Where unsupported, OS defaults apply for the timers.
 * - **JS / WasmJS / WebSocket transport**: not applicable; ignored.
 */
public data class SocketKeepAliveConfig(
	public val idle: Duration,
	public val interval: Duration,
	public val probeCount: Int,
) {
	public companion object {
		public val Default: SocketKeepAliveConfig =
			SocketKeepAliveConfig(
				idle = 10.seconds,
				interval = 5.seconds,
				probeCount = 3,
			)
	}
}

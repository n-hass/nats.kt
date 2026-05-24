package io.natskt.client.transport.internal

import io.ktor.network.sockets.Connection
import io.natskt.client.SocketKeepAliveConfig

/**
 * Apply platform-specific TCP keep-alive tuning to an already-connected [Connection]. Ktor's
 * `configure { keepAlive = true }` enables the boolean `SO_KEEPALIVE`; this hook installs
 * idle/interval/probe-count where the platform supports doing so. Targets that can't tune
 * (or for which Ktor's boolean flag is already sufficient) no-op.
 */
internal expect fun tuneSocketKeepAlive(
	connection: Connection,
	config: SocketKeepAliveConfig,
)

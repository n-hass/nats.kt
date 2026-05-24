package io.natskt.client.transport.internal

import io.ktor.network.sockets.Connection
import io.natskt.client.SocketKeepAliveConfig

internal actual fun tuneSocketKeepAlive(
	connection: Connection,
	config: SocketKeepAliveConfig,
) {
	// Not applicable on WasmJs targets.
}

package io.natskt.client.transport.internal

import io.ktor.network.sockets.Connection
import io.natskt.client.SocketKeepAliveConfig

internal actual fun tuneSocketKeepAlive(
	connection: Connection,
	config: SocketKeepAliveConfig,
) {
	// Ktor's TCP socket options already set SO_KEEPALIVE; per-socket idle/interval/probe tuning
	// on the JVM requires jdk.net.ExtendedSocketOptions on a SocketChannel that Ktor doesn't
	// expose directly. Leaving the timers at OS defaults until that plumbing is justified.
}

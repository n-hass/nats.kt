package io.natskt.client.transport

import io.ktor.network.sockets.connection
import io.ktor.network.tls.tls

internal actual suspend fun performTlsUpgrade(transport: TcpTransport): Transport =
	TcpTransport(transport.inner.tls(transport.context).connection(), transport.context, transport.serverName)

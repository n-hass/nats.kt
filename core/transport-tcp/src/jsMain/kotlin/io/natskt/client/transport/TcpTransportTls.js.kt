package io.natskt.client.transport

internal actual suspend fun performTlsUpgrade(transport: TcpTransport): Transport =
	throw UnsupportedOperationException("TLS upgrade is not supported on JS; use WebSocket transport with wss://")

package io.natskt.client.transport

import io.natskt.tls.nativeTls

internal actual suspend fun performTlsUpgrade(transport: TcpTransport): Transport {
	val tls =
		transport.inner.nativeTls(transport.context) {
			serverName = transport.serverName
			verifyCertificates = transport.verifyCertificates
		}
	return NativeTlsTransportAdapter(transport.inner, tls, transport.context)
}

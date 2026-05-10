package io.natskt.client.transport

import io.natskt.tls.nativeTls

internal actual suspend fun performTlsUpgrade(transport: TcpTransport): Transport {
	val cfg = transport.tlsConfig
	if (cfg.hasClientCertificate) {
		throw UnsupportedOperationException(
			"Mutual TLS (clientCertificate) is not yet supported on Kotlin/Native targets. " +
				"Use a JVM target or a WebSocket transport on a platform whose Ktor engine supports it.",
		)
	}
	val tls =
		transport.inner.nativeTls(transport.context) {
			serverName = transport.serverName
			verifyCertificates = !cfg.acceptAnyServerCertificate
			trustAnchorsDer = cfg.caCertificatesDer
		}
	return NativeTlsTransportAdapter(transport.inner, tls, transport.context)
}

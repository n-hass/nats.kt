package io.natskt.tls

import io.ktor.network.sockets.Connection
import io.natskt.tls.internal.TlsHandshake
import kotlin.coroutines.CoroutineContext

public suspend fun Connection.nativeTls(
	coroutineContext: CoroutineContext,
	block: NativeTlsConfigBuilder.() -> Unit = {},
): NativeTlsConnection {
	val config = NativeTlsConfigBuilder().apply(block)
	val handshake =
		TlsHandshake(
			rawInput = input,
			rawOutput = output,
			serverName = config.serverName,
			coroutineContext = coroutineContext,
			verifyCertificates = config.verifyCertificates,
			trustAnchorsDer = config.trustAnchorsDer,
		)
	return try {
		handshake.negotiate()
		NativeTlsConnection(handshake)
	} catch (cause: Throwable) {
		handshake.close()
		throw cause
	}
}

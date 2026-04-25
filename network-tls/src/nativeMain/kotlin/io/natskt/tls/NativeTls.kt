package io.natskt.tls

import io.ktor.network.sockets.Connection
import io.natskt.tls.internal.TlsHandshake
import kotlin.coroutines.CoroutineContext

public suspend fun Connection.nativeTls(
	coroutineContext: CoroutineContext,
	block: NativeTlsConfigBuilder.() -> Unit = {},
): NativeTlsConnection {
	val config = NativeTlsConfigBuilder().apply(block)
	val handshake = TlsHandshake(input, output, config.serverName, coroutineContext, config.verifyCertificates)
	return try {
		handshake.negotiate()
		NativeTlsConnection(handshake)
	} catch (cause: Throwable) {
		handshake.close()
		throw cause
	}
}

package io.natskt.tls

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.natskt.tls.internal.TlsHandshake
import kotlin.coroutines.CoroutineContext

internal actual suspend fun performNativeTlsHandshake(
	connection: Connection,
	coroutineContext: CoroutineContext,
	@Suppress("UNUSED_PARAMETER") selectorManager: SelectorManager?,
	config: NativeTlsConfigBuilder,
): NativeTlsConnection {
	val handshake =
		TlsHandshake(
			rawInput = connection.input,
			rawOutput = connection.output,
			serverName = config.serverName,
			coroutineContext = coroutineContext,
			verifyCertificates = config.verifyCertificates,
			trustAnchorsDer = config.trustAnchorsDer,
		)
	try {
		handshake.negotiate()
	} catch (cause: Throwable) {
		handshake.close()
		throw cause
	}
	return NativeTlsConnection(
		input = handshake.appDataInput,
		output = handshake.appDataOutput,
		closer = { handshake.closeGracefully() },
	)
}

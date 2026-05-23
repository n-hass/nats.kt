package io.natskt.tls

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.awaitClosed
import io.ktor.network.tls.TlsException
import io.natskt.tls.internal.NwTlsConnection
import io.natskt.tls.internal.buildSecureTcpParameters
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

private val logger = KotlinLogging.logger("AppleNativeTls")

/**
 * Apple TLS upgrade: closes the plaintext socket and opens a fresh `nw_connection_t` with TLS
 * parameters to the same `(host, port)`. Network.framework cannot adopt an existing connected fd,
 * so this is the only way to get a TLS session — see
 * [PlatformTlsBehavior.apple.kt][io.natskt.tls.internal.platformTlsUpgradeReopens]. The caller's
 * protocol engine is signalled via [io.natskt.client.transport.Transport.tlsUpgradeReopened] that
 * it must re-read INFO from the new connection before sending CONNECT.
 */
internal actual suspend fun performNativeTlsHandshake(
	connection: Connection,
	coroutineContext: CoroutineContext,
	@Suppress("UNUSED_PARAMETER") selectorManager: SelectorManager?,
	config: NativeTlsConfigBuilder,
): NativeTlsConnection {
	val remote =
		connection.socket.remoteAddress as? InetSocketAddress
			?: throw TlsException("Cannot determine remote address for TLS reopen on Apple")

	val host = config.serverName ?: remote.hostname
	val port = remote.port.toString()
	logger.trace { "closing plaintext socket and reopening with TLS to $host:$port" }

	// Tear down the plaintext socket the engine handed us — the new TLS session lives on a fresh
	// nw_connection_t.
	runCatching { connection.input.cancel(null) }
	runCatching { connection.output.cancel(null) }
	connection.socket.close()
	runCatching { connection.socket.awaitClosed() }

	val parameters = buildSecureTcpParameters(config)
	val scope = CoroutineScope(coroutineContext)
	val nwConnection = NwTlsConnection.open(host, port, parameters, scope)

	return NativeTlsConnection(
		input = nwConnection.input,
		output = nwConnection.output,
		closer = { nwConnection.close() },
	)
}

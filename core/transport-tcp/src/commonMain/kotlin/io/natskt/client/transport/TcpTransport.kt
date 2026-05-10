package io.natskt.client.transport

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.awaitClosed
import io.ktor.network.sockets.connection
import io.ktor.network.sockets.isClosed
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.natskt.client.NatsServerAddress
import io.natskt.client.TlsConfig
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

public class TcpTransport internal constructor(
	internal val inner: Connection,
	internal val context: CoroutineContext,
	private val selectorManager: SelectorManager,
	internal val serverName: String? = null,
	internal val tlsConfig: TlsConfig = TlsConfig.Default,
) : Transport,
	CoroutineScope by inner.socket {
	public companion object : TransportFactory {
		override suspend fun connect(
			address: NatsServerAddress,
			context: CoroutineContext,
			tlsConfig: TlsConfig,
		): Transport {
			val selectorManager = SelectorManager(context)
			return try {
				val connection =
					aSocket(selectorManager)
						.tcp()
						.connect(address.url.host, address.url.port) { }
						.connection()
				TcpTransport(
					connection,
					context,
					selectorManager,
					address.url.host,
					tlsConfig,
				)
			} catch (e: Throwable) {
				selectorManager.close()
				throw e
			}
		}
	}

	override val isClosed: Boolean by inner.socket::isClosed
	override val incoming: ByteReadChannel by inner::input

	override suspend fun close() {
		try {
			inner.socket.close()
			inner.socket.awaitClosed()
		} finally {
			selectorManager.close()
		}
	}

	override suspend fun upgradeTLS(): Transport = performTlsUpgrade(this)

	override suspend fun write(block: suspend (ByteWriteChannel) -> Unit) {
		block(inner.output)
	}

	override suspend fun flush() {
		inner.output.flush()
	}
}

internal expect suspend fun performTlsUpgrade(transport: TcpTransport): Transport

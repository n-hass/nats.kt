package io.natskt.client.transport

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.awaitClosed
import io.ktor.network.sockets.connection
import io.ktor.network.sockets.isClosed
import io.ktor.network.tls.tls
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.natskt.client.NatsServerAddress
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

public class TcpTransport internal constructor(
	private val inner: Connection,
	private val context: CoroutineContext,
) : Transport,
	CoroutineScope by inner.socket {
	public companion object : TransportFactory {
		override suspend fun connect(
			address: NatsServerAddress,
			context: CoroutineContext,
		): Transport =
			TcpTransport(
				aSocket(SelectorManager(context))
					.tcp()
					.connect(address.url.host, address.url.port) { }
					.connection(),
				context,
			)
	}

	override val isClosed: Boolean by inner.socket::isClosed
	override val incoming: ByteReadChannel by inner::input

	override suspend fun close() {
		inner.socket.close()
		inner.socket.awaitClosed()
	}

	override suspend fun upgradeTLS(): TcpTransport = TcpTransport(inner.tls(context).connection(), context)

	override suspend fun write(block: suspend (ByteWriteChannel) -> Unit) {
		block(inner.output)
	}

	override suspend fun flush() {
		inner.output.flush()
	}
}

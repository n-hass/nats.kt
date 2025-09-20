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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

public class TcpTransport internal constructor(
    private val inner: Connection,
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
            )
    }

    private val writeMutex = Mutex()

    override val isClosed: Boolean by inner.socket::isClosed
    override val incoming: ByteReadChannel by inner::input

    override suspend fun close() {
        inner.socket.close()
        inner.socket.awaitClosed()
    }

    override suspend fun upgradeTLS(): TcpTransport = upgradeTlsNative(inner)

    override suspend fun write(block: suspend (ByteWriteChannel) -> Unit): Unit =
        writeMutex.withLock {
            block(inner.output)
        }

    override suspend fun flush(): Unit =
        writeMutex.withLock {
            inner.output.flush()
        }
}

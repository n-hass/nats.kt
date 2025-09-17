package io.natskt.client.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.availableForRead
import io.ktor.utils.io.close
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.reader
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.natskt.client.NatsServerAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmInline

public data class WebSocketTransport(
    val session: DefaultClientWebSocketSession,
) : Transport,
    CoroutineScope by session {
    @JvmInline
    public value class Factory(
        public val httpClient: HttpClient,
    ) : TransportFactory {
        public constructor(engine: HttpClientEngineFactory<*>) : this(
            HttpClient(engine) {
                install(WebSockets)
            },
        )

        override suspend fun connect(
            address: NatsServerAddress,
            context: CoroutineContext,
        ): Transport = WebSocketTransport(httpClient.webSocketSession("ws://${address.url.host}:${address.url.port}"))
    }

    private val outgoing =
        session
            .reader {
                while (isActive) {
                    channel.awaitContent()

                    val packet = channel.readRemaining(channel.availableForRead.toLong())
                    session.send(Frame.Binary(true, packet))
                }
            }.channel

    override val incoming: ByteReadChannel =
        session
            .writer(autoFlush = true) {
                while (true) {
                    val frame =
                        try {
                            session.incoming.receive()
                        } catch (ex: CancellationException) {
                            channel.close(ex)
                            break
                        }

                    when (frame) {
                        is Frame.Binary -> {
                            channel.writeFully(frame.data)
                            channel.flush()
                        }
                        is Frame.Text -> {
                            val bytes = frame.data
                            channel.writeFully(bytes)
                            channel.flush()
                        }
                        is Frame.Close -> {
                            channel.close()
                            break
                        }
                        else -> {
                            // Ping/Pong/other control frames -> ignore for stream
                        }
                    }
                }
            }.channel

    override val isClosed: Boolean get() = !session.isActive

    override suspend fun close() {
        session.close()
    }

    override suspend fun upgradeTLS(): Transport = this

    override suspend fun write(block: suspend (ByteWriteChannel) -> Unit) {
        block(outgoing)
        outgoing.flush()
    }

    override suspend fun flush() {
        session.flush()
    }
}

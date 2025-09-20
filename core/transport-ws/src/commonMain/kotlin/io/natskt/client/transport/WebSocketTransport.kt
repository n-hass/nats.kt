package io.natskt.client.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.http.URLProtocol
import io.ktor.http.fullPath
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.close
import io.ktor.utils.io.readAvailable
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

@ConsistentCopyVisibility
public data class WebSocketTransport internal constructor(
	private val httpClient: HttpClient,
	private val address: NatsServerAddress,
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
		): Transport =
			WebSocketTransport(
				httpClient,
				address,
				httpClient.webSocketSession {
					url(address.url)
				},
			)
	}

	private val outgoing =
		session
			.reader {
				val buf = ByteArray(8192)
				while (isActive) {
					val n = channel.readAvailable(buf, 0, buf.size)
					when (n) {
						-1 -> {
							println("writer closed")
							break
						}
						0 -> {
							if (!channel.awaitContent()) break
							continue
						}
						else -> {
							session.send(Frame.Binary(fin = true, data = buf.copyOf(n)))
						}
					}
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
						is Frame.Binary, is Frame.Text -> {
							channel.writeFully(frame.data)
							channel.flush()
						}
						is Frame.Close -> {
							channel.flushAndClose()
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

	override suspend fun upgradeTLS(): Transport =
		WebSocketTransport(
			httpClient,
			address,
			httpClient.webSocketSession {
				if (address.url.protocolOrNull != null) {
					url(address.url)
				} else {
					url(scheme = URLProtocol.WSS.name, host = address.url.host, port = address.url.port, path = address.url.fullPath)
				}
			},
		)

	override suspend fun write(block: suspend (ByteWriteChannel) -> Unit) {
		block(outgoing)
		outgoing.flush()
	}

	override suspend fun flush() {
		session.flush()
	}
}

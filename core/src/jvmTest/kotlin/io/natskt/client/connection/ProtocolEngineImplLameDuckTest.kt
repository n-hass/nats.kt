package io.natskt.client.connection

import io.ktor.http.Url
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.natskt.api.CloseReason
import io.natskt.api.ConnectionPhase
import io.natskt.api.internal.ClientOperation
import io.natskt.api.internal.Operation
import io.natskt.api.internal.OperationSerializer
import io.natskt.api.internal.ParsedOutput
import io.natskt.api.internal.ServerOperation
import io.natskt.client.NatsServerAddress
import io.natskt.client.transport.Transport
import io.natskt.client.transport.TransportFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolEngineImplLameDuckTest {
	@Test
	fun `handshake aborts when server is already in lame duck`() =
		runBlocking {
			val info = info(ldm = true)
			val serializer = QueueSerializer(listOf(info))
			val engine = engine(serializer)

			engine.start()

			assertEquals(CloseReason.LameDuckMode, engine.closed.await())
			assertEquals(ConnectionPhase.LameDuck, engine.state.value.phase)
		}

	@Test
	fun `lame duck info update closes the connection`() =
		runBlocking {
			val serializer = QueueSerializer(listOf(info(ldm = false), info(ldm = true), Operation.Empty))
			val engine = engine(serializer)

			engine.start()

			assertEquals(CloseReason.LameDuckMode, engine.closed.await())
			assertEquals(ConnectionPhase.LameDuck, engine.state.value.phase)
		}

	private fun engine(serializer: OperationSerializer): ProtocolEngineImpl {
		val transport = StubTransport()
		return ProtocolEngineImpl(
			transportFactory = StubTransportFactory(transport),
			address = NatsServerAddress(Url("nats://localhost:4222")),
			parser = serializer,
			subscriptions = emptyMap(),
			serverInfo = MutableStateFlow(null),
			credentials = null,
			tlsRequired = false,
			scope = CoroutineScope(Dispatchers.Unconfined),
		)
	}

	private fun info(ldm: Boolean): ServerOperation.InfoOp =
		ServerOperation.InfoOp(
			serverId = "id",
			serverName = "name",
			version = "1",
			go = "go",
			host = "localhost",
			port = 4222,
			headers = true,
			maxPayload = 1024,
			proto = 1,
			clientId = null,
			authRequired = true,
			tlsRequired = null,
			tlsVerify = null,
			tlsAvailable = null,
			connectUrls = null,
			wsConnectUrls = null,
			ldm = ldm,
			gitCommit = null,
			jetstream = null,
			ip = null,
			clientIp = null,
			nonce = "nonce",
			cluster = null,
			domain = null,
			xkey = null,
		)

	private class QueueSerializer(
		values: List<ParsedOutput>,
	) : OperationSerializer {
		private val queue = ArrayDeque(values)

		override suspend fun parse(channel: ByteReadChannel): ParsedOutput? = queue.removeFirstOrNull()

		override fun encode(op: ClientOperation): ByteArray = ByteArray(0)
	}

	private class StubTransportFactory(
		private val transport: StubTransport,
	) : TransportFactory {
		override suspend fun connect(
			address: NatsServerAddress,
			context: CoroutineContext,
		): Transport = transport
	}

	private class StubTransport : Transport {
		private val incomingChannel = ByteChannel(autoFlush = true)
		private var closed = false

		override val coroutineContext: CoroutineContext = EmptyCoroutineContext

		override val isClosed: Boolean
			get() = closed

		override val incoming: ByteReadChannel
			get() = incomingChannel

		override suspend fun close() {
			closed = true
			incomingChannel.cancel(null)
		}

		override suspend fun upgradeTLS(): Transport = this

		override suspend fun write(block: suspend (ByteWriteChannel) -> Unit) {
			block(ByteChannel(autoFlush = true))
		}

		override suspend fun flush() { }
	}
}

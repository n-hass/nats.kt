@file:OptIn(ExperimentalCoroutinesApi::class)

package io.natskt.client.connection

import io.ktor.http.Url
import io.ktor.util.collections.ConcurrentMap
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readRemaining
import io.natskt.api.internal.OperationEncodeBuffer
import io.natskt.api.internal.OperationSerializer
import io.natskt.client.NatsServerAddress
import io.natskt.client.transport.Transport
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.ClientOperation
import io.natskt.internal.Operation
import io.natskt.internal.ParsedOutput
import io.natskt.internal.PendingRequest
import io.natskt.internal.ServerOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.readByteArray
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

class ProtocolEngineImplWriterTest {
	@Test
	fun `writer batches ops until flush`() =
		runTest {
			val transport = RecordingTransport(coroutineContext)
			val engine =
				engine(
					serializer = BatchingSerializer(),
					transportFactory = RecordingTransportFactory(transport),
					scope = this,
				)

			engine.start()
			val handshakeFlushes = transport.flushCount

			engine.send(
				ClientOperation.PubOp(
					subject = "s1",
					replyTo = null,
					payload = "a".encodeToByteArray(),
				),
			)
			engine.send(
				ClientOperation.PubOp(
					subject = "s2",
					replyTo = null,
					payload = "b".encodeToByteArray(),
				),
			)
			runCurrent()

			assertEquals(handshakeFlushes, transport.flushCount, "flush should not run per op")

			engine.close()
			runCurrent()

			val payloadWrites = transport.writes.lastOrNull() ?: fail("expected buffered writes")
			assertContentEquals("PUB s1 1\r\na\r\nPUB s2 1\r\nb\r\n".encodeToByteArray(), payloadWrites)
			assertEquals(handshakeFlushes + 1, transport.flushCount, "close should flush pending buffer once")
		}

	@Test
	fun `writer encodes utf8 data`() =
		runTest {
			val transport = RecordingTransport(coroutineContext)
			val utf8Line = "utf8-\u00b5\u96ea\ud83d\ude00"
			val engine =
				engine(
					serializer = Utf8Serializer(utf8Line),
					transportFactory = RecordingTransportFactory(transport),
					scope = this,
					writeBufferLimitBytes = 4,
				)

			engine.start()
			engine.close()
			runCurrent()

			val written = transport.writes.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
			assertContentEquals("$utf8Line\r\n".encodeToByteArray(), written)
		}

	private fun engine(
		serializer: OperationSerializer,
		transportFactory: TransportFactory,
		scope: CoroutineScope,
		writeBufferLimitBytes: Int = 1024,
	): ProtocolEngineImpl =
		ProtocolEngineImpl(
			transportFactory = transportFactory,
			address = NatsServerAddress(Url("nats://localhost:4222")),
			parser = serializer,
			subscriptions = emptyMap(),
			pendingRequests = ConcurrentMap<String, PendingRequest>(),
			serverInfo = MutableStateFlow(null),
			credentials = null,
			tlsRequired = false,
			operationBufferCapacity = 32,
			writeBufferLimitBytes = writeBufferLimitBytes,
			writeFlushIntervalMs = 10_000,
			scope = scope,
		)

	private fun defaultInfo(): ServerOperation.InfoOp =
		ServerOperation.InfoOp(
			serverId = "test",
			serverName = "srv",
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
			ldm = null,
			gitCommit = null,
			jetstream = null,
			ip = null,
			clientIp = null,
			nonce = null,
			cluster = null,
			domain = null,
			xkey = null,
		)

	private inner class BatchingSerializer : OperationSerializer {
		private var first = true

		override suspend fun parse(channel: ByteReadChannel): ParsedOutput {
			if (first) {
				first = false
				return defaultInfo()
			}
			while (true) {
				if (!channel.awaitContent()) return Operation.Empty
			}
		}

		override suspend fun encode(
			op: ClientOperation,
			buffer: OperationEncodeBuffer,
		) {
			when (op) {
				is ClientOperation.ConnectOp -> buffer.writeUtf8("CONNECT")
				is ClientOperation.PubOp -> {
					buffer.writeUtf8("PUB ${op.subject} ${op.payload?.size ?: 0}")
					buffer.writeCrLf()
					op.payload?.let { buffer.writeBytes(it) }
				}
				else -> buffer.writeUtf8(op.toString())
			}
			buffer.writeCrLf()
		}
	}

	private inner class Utf8Serializer(
		private val line: String,
	) : OperationSerializer {
		private var first = true

		override suspend fun parse(channel: ByteReadChannel): ParsedOutput {
			if (first) {
				first = false
				return defaultInfo()
			}
			while (true) {
				if (!channel.awaitContent()) return Operation.Empty
			}
		}

		override suspend fun encode(
			op: ClientOperation,
			buffer: OperationEncodeBuffer,
		) {
			buffer.writeUtf8(line)
			buffer.writeCrLf()
		}
	}

	private class RecordingTransportFactory(
		private val transport: RecordingTransport,
	) : TransportFactory {
		override suspend fun connect(
			address: NatsServerAddress,
			context: CoroutineContext,
		): Transport = transport
	}

	private class RecordingTransport(
		override val coroutineContext: CoroutineContext = EmptyCoroutineContext,
	) : Transport {
		private val incomingChannel = ByteChannel(autoFlush = true)

		val writes = mutableListOf<ByteArray>()
		var flushCount = 0
		private var closed = false

		override val isClosed: Boolean
			get() = closed

		override val incoming: ByteReadChannel
			get() = incomingChannel

		override suspend fun close() {
			closed = true
			incomingChannel.close()
		}

		override suspend fun upgradeTLS(): Transport = this

		override suspend fun write(block: suspend (ByteWriteChannel) -> Unit) {
			val channel = ByteChannel(autoFlush = true)
			block(channel)
			channel.flush()
			channel.close()
			writes.add(channel.readRemaining().readByteArray())
		}

		override suspend fun flush() {
			flushCount++
		}
	}
}

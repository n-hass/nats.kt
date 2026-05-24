package io.natskt.client.connection

import io.ktor.http.Url
import io.ktor.util.collections.ConcurrentMap
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.natskt.api.CloseReason
import io.natskt.api.StaleConnectionException
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProtocolEngineImplPingHeartbeatTest {
	@Test
	fun `heartbeat fires StaleConnection after maxPingsOut unanswered`() =
		runBlocking {
			val transport = SilentTransport()
			val engine =
				ProtocolEngineImpl(
					transportFactory = SilentTransportFactory(transport),
					address = NatsServerAddress(Url("nats://localhost:4222")),
					parser = HandshakeOnlySerializer(),
					subscriptions = emptyMap(),
					pendingRequests = ConcurrentMap<String, PendingRequest>(),
					serverInfo = MutableStateFlow(null),
					credentials = null,
					name = null,
					tlsRequired = false,
					tlsConfig = io.natskt.client.TlsConfig.Default,
					socketKeepAlive = null,
					pingIntervalMs = 100,
					maxPingsOut = 2,
					noResponders = true,
					echo = false,
					supportUtf8Subjects = false,
					operationBufferCapacity = 32,
					writeBufferLimitBytes = 64 * 1024,
					scope = CoroutineScope(Dispatchers.Default),
				)

			engine.start()

			val reason = engine.closed.await()
			assertIs<CloseReason.IoError>(reason)
			val cause = reason.cause
			assertIs<StaleConnectionException>(cause)
			assertTrue(
				cause.outstandingPings >= 2,
				"outstanding count should be at or above maxPingsOut, was ${cause.outstandingPings}",
			)
		}

	private class SilentTransportFactory(
		private val transport: Transport,
	) : TransportFactory {
		override suspend fun connect(
			address: NatsServerAddress,
			context: CoroutineContext,
			tlsConfig: io.natskt.client.TlsConfig,
			socketKeepAlive: io.natskt.client.SocketKeepAliveConfig?,
		): Transport = transport
	}

	private class SilentTransport : Transport {
		private val incomingChannel = ByteChannel(autoFlush = true)
		private var closedFlag = false

		override val coroutineContext: CoroutineContext = EmptyCoroutineContext
		override val isClosed: Boolean get() = closedFlag
		override val incoming: ByteReadChannel get() = incomingChannel

		override suspend fun close() {
			closedFlag = true
			incomingChannel.cancel(null)
		}

		override suspend fun upgradeTLS(): Transport = this

		override suspend fun write(block: suspend (ByteWriteChannel) -> Unit) {
			block(ByteChannel(autoFlush = true))
		}

		override suspend fun flush() { }
	}

	private class HandshakeOnlySerializer : OperationSerializer {
		private var emittedInfo = false

		override suspend fun parse(channel: ByteReadChannel): ParsedOutput {
			if (!emittedInfo) {
				emittedInfo = true
				return ServerOperation.InfoOp(
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
					authRequired = false,
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
			}
			while (true) {
				if (!channel.awaitContent()) return Operation.Empty
			}
		}

		override suspend fun encode(
			op: ClientOperation,
			buffer: OperationEncodeBuffer,
		) {
			buffer.writeUtf8(op::class.simpleName ?: "?")
			buffer.writeCrLf()
		}
	}
}

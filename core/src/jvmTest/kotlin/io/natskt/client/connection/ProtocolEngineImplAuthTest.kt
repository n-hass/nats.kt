package io.natskt.client.connection

import io.ktor.http.Url
import io.ktor.util.collections.ConcurrentMap
import io.ktor.utils.io.ByteReadChannel
import io.natskt.api.Credentials
import io.natskt.api.internal.OperationEncodeBuffer
import io.natskt.api.internal.OperationSerializer
import io.natskt.client.NatsServerAddress
import io.natskt.client.transport.Transport
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.ClientOperation
import io.natskt.internal.ParsedOutput
import io.natskt.internal.PendingRequest
import io.natskt.internal.ServerOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProtocolEngineImplAuthTest {
	@Test
	fun `uses url credentials when none are provided`() {
		val engine = engine(url = "nats://alice:secret@localhost:4222")
		val connect = engine.invokeConnect(defaultInfo())
		assertEquals("alice", connect.user)
		assertEquals("secret", connect.pass)
	}

	@Test
	fun `explicit password credentials override url credentials`() {
		val creds = Credentials.Password(username = "carol", password = "topsecret")
		val engine = engine(url = "nats://alice:secret@localhost:4222", credentials = creds)
		val connect = engine.invokeConnect(defaultInfo())
		assertEquals("carol", connect.user)
		assertEquals("topsecret", connect.pass)
	}

	@Test
	fun `blank explicit credentials fall back to url credentials`() {
		val creds = Credentials.Password(username = "", password = "")
		val engine = engine(url = "nats://bob:password@localhost:4222", credentials = creds)
		val connect = engine.invokeConnect(defaultInfo())
		assertEquals("bob", connect.user)
		assertEquals("password", connect.pass)
	}

	@Test
	fun `url username is preserved when password is missing`() {
		val engine = engine(url = "nats://solo@localhost:4222")
		val connect = engine.invokeConnect(defaultInfo())
		assertEquals("solo", connect.user)
		assertNull(connect.pass)
	}

	private fun engine(
		url: String,
		credentials: Credentials? = null,
	): ProtocolEngineImpl =
		ProtocolEngineImpl(
			transportFactory = FakeTransportFactory,
			address = NatsServerAddress(Url(url)),
			parser = NoopSerializer,
			subscriptions = emptyMap(),
			pendingRequests = ConcurrentMap<String, PendingRequest>(),
			serverInfo = MutableStateFlow(null),
			credentials = credentials,
			tlsRequired = false,
			operationBufferCapacity = 32,
			writeBufferLimitBytes = 64 * 1024,
			writeFlushIntervalMs = 5,
			scope =
				object : CoroutineScope {
					override val coroutineContext: CoroutineContext = EmptyCoroutineContext
				},
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

	private fun ProtocolEngineImpl.invokeConnect(info: ServerOperation.InfoOp): ClientOperation.ConnectOp {
		val method = ProtocolEngineImpl::class.java.getDeclaredMethod("buildConnectOp", ServerOperation.InfoOp::class.java)
		method.isAccessible = true
		@Suppress("UNCHECKED_CAST")
		return method.invoke(this, info) as ClientOperation.ConnectOp
	}

	private object FakeTransportFactory : TransportFactory {
		override suspend fun connect(
			address: NatsServerAddress,
			context: CoroutineContext,
		): Transport = throw UnsupportedOperationException("not used in test")
	}

	private object NoopSerializer : OperationSerializer {
		override suspend fun parse(channel: ByteReadChannel): ParsedOutput? = null

		override suspend fun encode(
			op: ClientOperation,
			buffer: OperationEncodeBuffer,
		) { }
	}
}

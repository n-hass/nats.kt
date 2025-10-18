@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.natskt.client

import io.natskt.NatsClient
import io.natskt.api.CloseReason
import io.natskt.api.ConnectionPhase
import io.natskt.api.ConnectionState
import io.natskt.api.Subject
import io.natskt.api.from
import io.natskt.api.internal.ProtocolEngine
import io.natskt.internal.ClientOperation
import io.natskt.internal.ServerOperation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NatsClientImplTest {
	private fun newClient(scope: CoroutineScope): Pair<NatsClientImpl, FakeProtocolEngine> {
		val config =
			ClientConfigurationBuilder()
				.apply {
					server = "nats://localhost:4222"
					this.scope = scope
				}.build()

		val client = NatsClientImpl(config)
		val fakeEngine = FakeProtocolEngine()
		client.connectionManager.current.value = fakeEngine
		return client to fakeEngine
	}

	@Test
	fun `publish validates subjects`() =
		runTest {
			val (client, engine) = newClient(this.backgroundScope)

			assertFailsWith<IllegalArgumentException> {
				client.publish("has space", byteArrayOf())
			}

			client.publish("demo", byteArrayOf(1, 2, 3))

			val op = engine.sent.single() as ClientOperation.PubOp
			assertEquals("demo", op.subject)
			assertEquals(byteArrayOf(1, 2, 3).toList(), op.payload!!.toList())
		}

	@Test
	fun `publish with headers uses HPubOp`() =
		runTest {
			val (client, engine) = newClient(this.backgroundScope)

			client.publish(
				subject = Subject.from("demo"),
				message = byteArrayOf(4, 5, 6),
				headers = mapOf("Header" to listOf("value")),
				replyTo = Subject.from("inbox"),
			)

			val op = engine.sent.single() as ClientOperation.HPubOp
			assertEquals("demo", op.subject)
			assertEquals("inbox", op.replyTo)
			assertEquals(mapOf("Header" to listOf("value")), op.headers)
			assertEquals(byteArrayOf(4, 5, 6).toList(), op.payload!!.toList())
		}

	@Test
	fun `dsl builder produces nats client`() {
		val client = NatsClient { server = "nats://localhost:4222" }
		val impl = client as NatsClientImpl

		assertEquals(
			"nats://localhost:4222",
			impl.configuration.servers
				.single()
				.url
				.toString(),
		)
	}

	private class FakeProtocolEngine : ProtocolEngine {
		val sent = mutableListOf<ClientOperation>()

		override val serverInfo = MutableStateFlow<ServerOperation.InfoOp?>(null)

		override val state =
			MutableStateFlow(
				ConnectionState(
					phase = ConnectionPhase.Idle,
					rtt = null,
					lastPingAt = null,
					lastPongAt = null,
					messagesIn = 0u,
					messagesOut = 0u,
				),
			)

		override val closed: CompletableDeferred<CloseReason> = CompletableDeferred()

		override suspend fun send(op: ClientOperation) {
			sent += op
		}

		override suspend fun start() {}

		override suspend fun ping() {}

		override suspend fun drain(timeout: kotlin.time.Duration) {}

		override suspend fun close() {
			if (!closed.isCompleted) {
				closed.complete(CloseReason.CleanClose)
			}
		}
	}
}

@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.natskt.client

import io.natskt.NatsClient
import io.natskt.api.CloseReason
import io.natskt.api.ConnectionPhase
import io.natskt.api.ConnectionState
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.api.from
import io.natskt.api.internal.ProtocolEngine
import io.natskt.internal.ClientOperation
import io.natskt.internal.OutgoingMessage
import io.natskt.internal.RequestSubscriptionImpl
import io.natskt.internal.ServerOperation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NatsClientImplTest {
	private fun newClient(scope: CoroutineScope): Pair<NatsClientImpl, FakeProtocolEngine> {
		val config =
			ClientConfigurationBuilder()
				.apply {
					server = "nats://localhost:4222"
					this.scope = scope
				}.build()

		val client = NatsClient(config) as NatsClientImpl
		val fakeEngine = FakeProtocolEngine()
		client.connectionManager.current.value = fakeEngine
		return client to fakeEngine
	}

	@Test
	fun `publish validates subjects`() =
		runTest {
			val (client, engine) = newClient(this)

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
			val (client, engine) = newClient(this)

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
	fun `request registers inbox and completes when response arrives`() =
		runTest {
			val (client, engine) = newClient(this)

			val responseDeferred =
				client.request(
					subject = "service",
					message = byteArrayOf(1),
					headers = null,
					timeoutMs = 5_000,
					launchIn = this,
				)

			val subOp = engine.sent.filterIsInstance<ClientOperation.SubOp>().single()
			val pubOp = engine.sent.filterIsInstance<ClientOperation.PubOp>().single()
			assertEquals("service", pubOp.subject)
			assertEquals(subOp.subject, pubOp.replyTo)

			val inbox = client.subscriptions[subOp.sid]
			val requestSub = assertIs<RequestSubscriptionImpl>(inbox)

			requestSub.emit(
				OutgoingMessage(
					subject = Subject.from("reply"),
					replyTo = null,
					headers = null,
					data = "payload".encodeToByteArray(),
				),
			)

			val response = responseDeferred.await()
			assertEquals("payload", response.data!!.decodeToString())

			advanceUntilIdle()

			val unsub = engine.sent.filterIsInstance<ClientOperation.UnSubOp>().single()
			assertEquals(subOp.sid, unsub.sid)
			assertTrue(client.subscriptions.isEmpty())
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

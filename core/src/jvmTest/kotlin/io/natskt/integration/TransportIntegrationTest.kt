package io.natskt.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.java.Java
import io.ktor.client.engine.okhttp.OkHttp
import io.natskt.NatsClient
import io.natskt.api.NatsClient
import io.natskt.api.internal.InternalNatsApi
import io.natskt.client.transport.TcpTransport
import io.natskt.client.transport.WebSocketTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class TransportIntegrationTest {
	private suspend fun CoroutineScope.testDelivery(c: NatsClient) {
		val received = mutableListOf<String>()
		val delayed = CompletableDeferred(Unit)
		val job =
			launch {
				withTimeout(5.seconds) {
					c
						.subscribe("test.sub")
						.messages
						.take(2)
						.onStart {
							delayed.complete(Unit)
						}.collect {
							received += it.data!!.decodeToString()
						}
				}
			}
		job.start()

		delayed.await()
		delay(100)
		c.publish("test.sub", "alpha".encodeToByteArray())
		c.publish("test.sub", "beta".encodeToByteArray())

		job.join()

		assertEquals(listOf("alpha", "beta"), received)

		c.disconnect()
	}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `receives messages with TCP transport`() =
		RemoteNatsHarness.runBlocking { server ->
			testDelivery(
				NatsClient {
					this.server = server.uri
					transport = TcpTransport
				}.also {
					it.connect()
				},
			)
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `receives messages with WebSocket CIO transport`() =
		RemoteNatsHarness.runBlocking { server ->
			testDelivery(
				NatsClient {
					this.server = server.websocketUri
					transport = WebSocketTransport.Factory(CIO)
				}.also {
					it.connect()
				},
			)
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `receives messages with WebSocket Java transport`() =
		RemoteNatsHarness.runBlocking { server ->
			testDelivery(
				NatsClient {
					this.server = server.websocketUri
					transport = WebSocketTransport.Factory(Java)
				}.also {
					it.connect()
				},
			)
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `receives messages with WebSocket OkHttp transport`() =
		RemoteNatsHarness.runBlocking { server ->
			testDelivery(
				NatsClient {
					this.server = server.websocketUri
					transport = WebSocketTransport.Factory(OkHttp)
				}.also {
					it.connect()
				},
			)
		}
}

package io.natskt.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.ktor.client.engine.darwin.Darwin
import io.natskt.NatsClient
import io.natskt.api.NatsClient
import io.natskt.api.internal.InternalNatsApi
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

class TransportAppleIntegrationTest {
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
	fun `receives messages with WebSocket Darwin transport`() =
		RemoteNatsHarness.runBlocking { server ->
			testDelivery(
				NatsClient {
					this.server = server.websocketUri
					transport = WebSocketTransport.Factory(Darwin)
					maxReconnects = 3
				}.also {
					it.connect()
				},
			)
		}
}

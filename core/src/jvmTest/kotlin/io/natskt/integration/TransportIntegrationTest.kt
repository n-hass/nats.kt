package io.natskt.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.NatsClient
import io.natskt.api.internal.InternalNatsApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class TransportIntegrationTest {
	@OptIn(InternalNatsApi::class)
	@Test
	fun `receives messages with Js transport`() =
		RemoteNatsHarness.runBlocking { server ->
			val c =
				NatsClient {
					this.server = server.uri
				}.also {
					println("connecting...")
					it.connect()
					println("connected")
				}

			val received = mutableListOf<String>()
			val job =
				launch {
					withTimeout(5.seconds) {
						c.subscribe("test.sub").messages.take(2).collect {
							received += it.data!!.decodeToString()
						}
					}
				}
			job.start()

			delay(1)
			c.publish("test.sub", "alpha".encodeToByteArray())
			c.publish("test.sub", "beta".encodeToByteArray())

			job.join()

			assertEquals(listOf("alpha", "beta"), received)

			c.disconnect()
		}
}

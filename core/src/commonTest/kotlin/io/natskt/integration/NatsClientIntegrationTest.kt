package io.natskt.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import harness.waitForLog
import io.natskt.NatsClient
import io.natskt.api.ConnectionPhase
import io.natskt.client.NatsClientImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NatsClientIntegrationTest {
	@Test
	fun `client can publish and receive message`() =
		RemoteNatsHarness.runBlocking { server ->
			val client =
				NatsClient {
					this.server = server.uri
				} as NatsClientImpl

			try {
				val connectResult = client.connect()
				println("connected")
				val logDump = server.logSnapshot().joinToString(separator = "\n")
				assertTrue(
					connectResult.isSuccess,
					"connect failed: ${connectResult.exceptionOrNull()}\nserver logs:\n$logDump",
				)

				assertEquals(ConnectionPhase.Connected, client.connectionManager.connectionState.value.phase)

				val subscription =
					client.subscribe(
						subject = "integration.demo",
						eager = true,
						replayBuffer = 1,
						unsubscribeOnLastCollector = true,
					)

				withTimeout(5_000) {
					subscription.isActive.first { it }
				}

				client.publish("integration.demo", "hello".encodeToByteArray())

				assertEquals(
					"hello",
					subscription.messages
						.take(1)
						.toList()
						.first()
						.data
						?.decodeToString(),
				)

				subscription.unsubscribe()
				withTimeout(5_000) {
					subscription.isActive.first { !it }
				}
			} finally {
				client.disconnect()
			}
		}

	@Test
	fun `client request engages inbox lifecycle`() =
		RemoteNatsHarness.runBlocking { server ->
			val client = NatsClient { this.server = server.uri } as NatsClientImpl

			try {
				val connectResult = client.connect()
				val logDump = server.logSnapshot().joinToString(separator = "\n")
				assertTrue(
					connectResult.isSuccess,
					"connect failed: ${connectResult.exceptionOrNull()}\nserver logs:\n$logDump",
				)

				assertEquals(ConnectionPhase.Connected, client.connectionManager.connectionState.value.phase)

				val result =
					runCatching {
						withTimeout(4_000) {
							client.request(
								subject = "integration.req",
								message = "ping".encodeToByteArray(),
								timeoutMs = 500,
							)
						}
					}

				assertTrue(result.isFailure)

				waitForLog(server) { it.contains("PUB integration.req") }
				waitForLog(server) { it.contains("SUB _INBOX") }

				withTimeout(5_000) {
					while (client.pendingRequests.isNotEmpty()) {
						delay(50)
					}
				}
			} finally {
				client.disconnect()
			}
		}
}

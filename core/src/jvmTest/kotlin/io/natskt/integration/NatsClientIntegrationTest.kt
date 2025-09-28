package io.natskt.integration

import harness.NatsServerHarness
import harness.waitForLog
import io.natskt.NatsClient
import io.natskt.api.ConnectionPhase
import io.natskt.client.NatsClientImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NatsClientIntegrationTest {
	@Test
	fun `client can publish and receive message`() =
		runBlocking<Unit> {
			NatsServerHarness().use { server ->
				val client = NatsClient { this.server = server.uri } as NatsClientImpl

				try {
					val connectResult = client.connect()
					assertTrue(
						connectResult.isSuccess,
						"connect failed: ${connectResult.exceptionOrNull()}\nserver logs:\n${server.logs.joinToString(separator = "\n")}",
					)

					assertEquals(ConnectionPhase.Connected, client.connectionManager.connectionStatus.value.phase)

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

					waitForLog(server) { it.contains("SUB integration.demo") }
					waitForLog(server) { it.contains("MSG_PAYLOAD: [\"hello\"]") }

					subscription.unsubscribe()
					withTimeout(5_000) {
						subscription.isActive.first { !it }
					}
				} finally {
					client.disconnect()
				}
			}
		}

	@Test
	fun `client request engages inbox lifecycle`() =
		runBlocking {
			NatsServerHarness().use { server ->
				val client = NatsClient { this.server = server.uri } as NatsClientImpl

				try {
					val connectResult = client.connect()
					assertTrue(
						connectResult.isSuccess,
						"connect failed: ${connectResult.exceptionOrNull()}\nserver logs:\n${server.logs.joinToString(separator = "\n")}",
					)

					assertEquals(ConnectionPhase.Connected, client.connectionManager.connectionStatus.value.phase)

					val result =
						runCatching {
							withTimeout(2_000) {
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
}

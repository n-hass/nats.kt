package io.natskt.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.NatsClient
import io.natskt.api.ConnectionPhase
import io.natskt.client.NatsClientImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests to verify that in request operations, the subscription (SUB) is always
 * sent to the server BEFORE the publish (PUB). This is critical for ensuring
 * that the inbox subscription is active before any responses can arrive.
 */
class RequestOrderingTest {
	@Test
	fun `core request sends SUB before PUB`() =
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

				// Get initial log count
				val initialLogCount = server.logSnapshot().size

				// Make a request that will timeout (no responder)
				val result =
					runCatching {
						withTimeout(4_000) {
							client.request(
								subject = "test.request.ordering",
								message = "test".encodeToByteArray(),
								timeoutMs = 500,
							)
						}
					}

				// Request should timeout since there's no responder
				assertTrue(result.isFailure, "Expected request to timeout")

				// Wait a bit for logs to be written
				delay(500)

				// Get all logs and extract just the new ones
				val allLogs = server.logSnapshot()
				val requestLogs = allLogs.drop(initialLogCount)

				// Find SUB and PUB operations in the logs
				val subIndex = requestLogs.indexOfFirst { it.contains("[SUB _INBOX") }
				val pubIndex = requestLogs.indexOfFirst { it.contains("[PUB test.request.ordering") }

				assertTrue(
					subIndex >= 0,
					"SUB operation not found in logs. Logs:\n${requestLogs.joinToString("\n")}",
				)
				assertTrue(
					pubIndex >= 0,
					"PUB operation not found in logs. Logs:\n${requestLogs.joinToString("\n")}",
				)
				assertTrue(
					subIndex < pubIndex,
					"SUB must appear before PUB in server logs. SUB at index $subIndex, PUB at index $pubIndex. " +
						"Logs:\n${requestLogs.joinToString("\n")}",
				)

				// Wait for pending requests to clear
				withTimeout(5_000) {
					while (client.pendingRequests.isNotEmpty()) {
						delay(50)
					}
				}
			} finally {
				client.disconnect()
			}
		}

	@Test
	fun `core request with responder sends SUB before PUB`() =
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

				// Create a responder subscription
				val responder =
					client.subscribe(
						subject = "test.request.with.responder",
						eager = true,
						replayBuffer = 10,
					)

				withTimeout(5_000) {
					responder.isActive.first { it }
				}

				// Launch a coroutine to respond to requests
				client.scope.launch {
					responder.messages.collect { msg ->
						msg.replyTo?.let { replyTo ->
							client.publish(
								subject = replyTo,
								message = "response".encodeToByteArray(),
							)
						}
					}
				}

				// Get initial log count
				val initialLogCount = server.logSnapshot().size

				// Make a request
				val result =
					runCatching {
						withTimeout(2_000) {
							client.request(
								subject = "test.request.with.responder",
								message = "test".encodeToByteArray(),
								timeoutMs = 1000,
							)
						}
					}

				// Request should succeed with responder
				assertTrue(result.isSuccess, "Expected request to succeed: ${result.exceptionOrNull()}")
				assertEquals("response", result.getOrNull()?.data?.decodeToString())

				// Wait a bit for logs to be written
				delay(200)

				// Get all logs and extract just the new ones
				val allLogs = server.logSnapshot()
				val newLogs = allLogs.drop(initialLogCount)

				// Find SUB and PUB operations in the logs
				// Note: we're looking for the inbox SUB, not the responder SUB
				val subIndex = newLogs.indexOfFirst { it.contains("SUB _INBOX") && !it.contains("test.request.with.responder") }
				val pubIndex = newLogs.indexOfFirst { it.contains("PUB test.request.with.responder") }

				assertTrue(
					subIndex >= 0,
					"SUB operation not found in logs. Logs:\n${newLogs.joinToString("\n")}",
				)
				assertTrue(
					pubIndex >= 0,
					"PUB operation not found in logs. Logs:\n${newLogs.joinToString("\n")}",
				)
				assertTrue(
					subIndex < pubIndex,
					"SUB must appear before PUB in server logs. SUB at index $subIndex, PUB at index $pubIndex. " +
						"Logs:\n${newLogs.joinToString("\n")}",
				)

				responder.unsubscribe()
			} finally {
				client.disconnect()
			}
		}
}

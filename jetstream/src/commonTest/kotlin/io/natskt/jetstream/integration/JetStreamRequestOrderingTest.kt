package io.natskt.jetstream.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.NatsClient
import io.natskt.api.ConnectionPhase
import io.natskt.jetstream.JetStreamClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests to verify that in JetStream request operations, the subscription (SUB) is always
 * sent to the server BEFORE the publish (PUB). This includes:
 * - JetStreamClient.request() which delegates to NatsClient.request()
 * - JetStreamClient.publish() which uses request internally for acks
 */
class JetStreamRequestOrderingTest {
	@Test
	fun `jetstream request sends SUB before PUB`() =
		RemoteNatsHarness.runBlocking { server ->
			val client = NatsClient { this.server = server.uri }
			val js = JetStreamClient(client)

			try {
				val connectResult = client.connect()
				val logDump = server.logSnapshot().joinToString(separator = "\n")
				assertTrue(
					connectResult.isSuccess,
					"connect failed: ${connectResult.exceptionOrNull()}\nserver logs:\n$logDump",
				)

				assertEquals(ConnectionPhase.Connected, client.connectionState.value.phase)

				// Get initial log count
				val initialLogCount = server.logSnapshot().size

				// Make a request that will timeout (no responder)
				val result =
					runCatching {
						withTimeout(2_000) {
							js.request(
								subject = "test.jetstream.request.ordering",
								message = "test",
								timeoutMs = 500,
							)
						}
					}

				// Request should timeout since there's no responder
				assertTrue(result.isFailure, "Expected request to timeout")

				// Wait a bit for logs to be written
				delay(200)

				// Get all logs and extract just the new ones
				val allLogs = server.logSnapshot()
				val newLogs = allLogs.drop(initialLogCount)

				// Find SUB and PUB operations in the logs
				val subIndex = newLogs.indexOfFirst { it.contains("SUB _INBOX") }
				val pubIndex = newLogs.indexOfFirst { it.contains("PUB test.jetstream.request.ordering") }

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
			} finally {
				client.disconnect()
			}
		}

	@Test
	fun `jetstream publish sends SUB before PUB`() =
		RemoteNatsHarness.runBlocking { server ->
			val client = NatsClient { this.server = server.uri }
			val js = JetStreamClient(client)

			try {
				val connectResult = client.connect()
				val logDump = server.logSnapshot().joinToString(separator = "\n")
				assertTrue(
					connectResult.isSuccess,
					"connect failed: ${connectResult.exceptionOrNull()}\nserver logs:\n$logDump",
				)

				// Create a stream for publishing
				val stream =
					js.manager.createStream {
						name = "test_request_ordering"
						subject("test.ordering.>")
					}

				// Get initial log count
				val initialLogCount = server.logSnapshot().size

				// Publish a message (this internally uses request for ack)
				val result =
					runCatching {
						withTimeout(2_000) {
							js.publish(
								subject = "test.ordering.message",
								message = "test message".encodeToByteArray(),
							)
						}
					}

				// Publish should succeed
				assertTrue(result.isSuccess, "Expected publish to succeed: ${result.exceptionOrNull()}")

				// Wait a bit for logs to be written
				delay(200)

				// Get all logs and extract just the new ones
				val allLogs = server.logSnapshot()
				val newLogs = allLogs.drop(initialLogCount)

				// Find SUB and PUB operations in the logs
				// JetStream publish uses request internally, so there should be a SUB for the ack inbox
				val subIndex = newLogs.indexOfFirst { it.contains("SUB _INBOX") }
				val pubIndex = newLogs.indexOfFirst { it.contains("PUB test.ordering.message") }

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
			} finally {
				client.disconnect()
			}
		}

	@Test
	fun `jetstream request with responder sends SUB before PUB`() =
		RemoteNatsHarness.runBlocking { server ->
			val client = NatsClient { this.server = server.uri }
			val js = JetStreamClient(client)

			try {
				val connectResult = client.connect()
				val logDump = server.logSnapshot().joinToString(separator = "\n")
				assertTrue(
					connectResult.isSuccess,
					"connect failed: ${connectResult.exceptionOrNull()}\nserver logs:\n$logDump",
				)

				assertEquals(ConnectionPhase.Connected, client.connectionState.value.phase)

				// Create a responder subscription
				val responder =
					client.subscribe(
						subject = "test.jetstream.request.with.responder",
						eager = true,
						replayBuffer = 10,
					)

				withTimeout(5_000) {
					responder.isActive.first { it }
				}

				// Launch a coroutine to respond to requests
				launch {
					responder.messages.collect { msg ->
						msg.replyTo?.let { replyTo ->
							client.publish(
								subject = replyTo,
								message = "jetstream response".encodeToByteArray(),
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
							js.request(
								subject = "test.jetstream.request.with.responder",
								message = "test",
								timeoutMs = 1000,
							)
						}
					}

				// Request should succeed with responder
				assertTrue(result.isSuccess, "Expected request to succeed: ${result.exceptionOrNull()}")
				assertEquals("jetstream response", result.getOrNull()?.data?.decodeToString())

				// Wait a bit for logs to be written
				delay(200)

				// Get all logs and extract just the new ones
				val allLogs = server.logSnapshot()
				val newLogs = allLogs.drop(initialLogCount)

				// Find SUB and PUB operations in the logs
				// Note: we're looking for the inbox SUB, not the responder SUB
				val subIndex = newLogs.indexOfFirst { it.contains("SUB _INBOX") && !it.contains("test.jetstream.request.with.responder") }
				val pubIndex = newLogs.indexOfFirst { it.contains("PUB test.jetstream.request.with.responder") }

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

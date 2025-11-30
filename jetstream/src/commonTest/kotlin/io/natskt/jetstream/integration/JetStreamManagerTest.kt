package io.natskt.jetstream.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.jetstream.api.AckPolicy
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.PurgeOptions
import io.natskt.jetstream.api.RetentionPolicy
import io.natskt.jetstream.api.StreamInfoOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class JetStreamManagerTest {
	@Test
	fun `it gets account statistics`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val stats = js.manager.getAccountStatistics()
				assertNotNull(stats)
				assertEquals(0, stats.streams)
				assertEquals(0, stats.consumers)
			}
		}

	@Test
	fun `it creates and updates a stream`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				val stream =
					js.manager.createStream {
						name = "test_update_stream"
						subject("test.update.>")
						retention = RetentionPolicy.Limits
						maxMessages = 1000
					}

				assertNotNull(stream)
				assertEquals(
					1000L,
					stream.info.value
						?.config
						?.maxMessages,
				)

				// Update stream
				val updatedInfo =
					js.manager.updateStream("test_update_stream") {
						maxMessages = 2000
						subject("test.2.>")
					}

				assertEquals(2000L, updatedInfo.config.maxMessages)
				assertEquals(listOf("test.2.>"), updatedInfo.config.subjects)

				// Clean up
				js.manager.deleteStream("test_update_stream")
			}
		}

	@Test
	fun `it deletes a stream`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_delete_stream"
					subject("test.delete.>")
				}

				// Delete stream
				val deleted = js.manager.deleteStream("test_delete_stream")
				assertTrue(deleted)

				// Verify it's gone
				val result = runCatching { js.manager.getStreamInfo("test_delete_stream") }
				assertTrue(result.isFailure)
			}
		}

	@Test
	fun `it gets stream info`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_stream_info"
					subject("test.info.>")
				}

				// Get stream info
				val info = js.manager.getStreamInfo("test_stream_info")
				assertNotNull(info)
				assertEquals("test_stream_info", info.config.name)

				// Clean up
				js.manager.deleteStream("test_stream_info")
			}
		}

	@Test
	fun `it gets stream info with options`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_stream_info_opts"
					subject("test.info.opts.>")
				}

				// Get stream info with options
				val info =
					js.manager.getStreamInfo(
						"test_stream_info_opts",
						StreamInfoOptions(subjectsFilter = "test.info.opts.>"),
					)
				assertNotNull(info)
				assertEquals("test_stream_info_opts", info.config.name)

				// Clean up
				js.manager.deleteStream("test_stream_info_opts")
			}
		}

	@Test
	fun `it purges a stream`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_purge_stream"
					subject("test.purge.>")
				}

				// Publish some messages
				js.publish("test.purge.1", "message1".encodeToByteArray())
				js.publish("test.purge.2", "message2".encodeToByteArray())
				js.publish("test.purge.3", "message3".encodeToByteArray())

				// Purge all
				val purgeResponse = js.manager.purgeStream("test_purge_stream")
				assertTrue(purgeResponse.success)
				assertEquals(3u, purgeResponse.purged)

				// Clean up
				js.manager.deleteStream("test_purge_stream")
			}
		}

	@Test
	fun `it purges a stream with options`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_purge_stream_opts"
					subject("test.purge.opts.>")
				}

				// Publish some messages
				js.publish("test.purge.opts.a", "message1".encodeToByteArray())
				js.publish("test.purge.opts.b", "message2".encodeToByteArray())
				js.publish("test.purge.opts.a", "message3".encodeToByteArray())

				// Purge only subject "test.purge.opts.a"
				val purgeResponse =
					js.manager.purgeStream(
						"test_purge_stream_opts",
						PurgeOptions(subject = "test.purge.opts.a"),
					)
				assertTrue(purgeResponse.success)
				assertEquals(2u, purgeResponse.purged)

				// Clean up
				js.manager.deleteStream("test_purge_stream_opts")
			}
		}

	@Test
	fun `it gets all stream names`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create streams
				js.manager.createStream {
					name = "test_names_1"
					subject("test.names.1.>")
				}
				js.manager.createStream {
					name = "test_names_2"
					subject("test.names.2.>")
				}

				// Get all stream names
				val names = js.manager.getStreamNames()
				assertTrue(names.contains("test_names_1"))
				assertTrue(names.contains("test_names_2"))

				// Clean up
				js.manager.deleteStream("test_names_1")
				js.manager.deleteStream("test_names_2")
			}
		}

	@Test
	fun `it gets stream names with filter`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create streams
				js.manager.createStream {
					name = "test_filter_1"
					subject("test.filter.a.>")
				}
				js.manager.createStream {
					name = "test_filter_2"
					subject("test.filter.b.>")
				}

				// Get stream names filtered by subject
				val names = js.manager.getStreamNames("test.filter.a.>")
				assertTrue(names.contains("test_filter_1"))

				// Clean up
				js.manager.deleteStream("test_filter_1")
				js.manager.deleteStream("test_filter_2")
			}
		}

	@Test
	fun `it gets all streams`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create streams
				js.manager.createStream {
					name = "test_get_streams_1"
					subject("test.get.streams.1.>")
				}
				js.manager.createStream {
					name = "test_get_streams_2"
					subject("test.get.streams.2.>")
				}

				// Get all streams
				val streams = js.manager.getStreams()
				assertTrue(streams.any { it.config.name == "test_get_streams_1" })
				assertTrue(streams.any { it.config.name == "test_get_streams_2" })

				// Clean up
				js.manager.deleteStream("test_get_streams_1")
				js.manager.deleteStream("test_get_streams_2")
			}
		}

	@Test
	fun `it creates and updates a consumer`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_consumer_stream"
					subject("test.consumer.>")
				}

				// Create consumer
				val consumerInfo =
					js.manager.createConsumer("test_consumer_stream") {
						durableName = "test_consumer"
						ackPolicy = AckPolicy.Explicit
						filterSubject = "test.consumer.>"
						description = "Initial description"
					}

				assertNotNull(consumerInfo)
				assertEquals("test_consumer", consumerInfo.name)

				// Update consumer (only mutable fields like description)
				val updatedInfo =
					js.manager.updateConsumer("test_consumer_stream") {
						durableName = "test_consumer"
						ackPolicy = AckPolicy.Explicit
						filterSubject = "test.consumer.>"
						description = "Updated description"
					}

				assertEquals("Updated description", updatedInfo.config.description)

				// Clean up
				js.manager.deleteConsumer("test_consumer_stream", "test_consumer")
				js.manager.deleteStream("test_consumer_stream")
			}
		}

	@Test
	fun `it adds or updates a consumer`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_addorupdate_stream"
					subject("test.addorupdate.>")
				}

				// Add or update (create)
				val consumerInfo1 =
					js.manager.createOrUpdateConsumer("test_addorupdate_stream") {
						durableName = "test_addorupdate_consumer"
						ackPolicy = AckPolicy.Explicit
						filterSubject = "test.addorupdate.>"
						description = "First description"
					}

				assertEquals("test_addorupdate_consumer", consumerInfo1.name)

				// Add or update (update - only mutable fields)
				val consumerInfo2 =
					js.manager.createOrUpdateConsumer("test_addorupdate_stream") {
						durableName = "test_addorupdate_consumer"
						ackPolicy = AckPolicy.Explicit
						filterSubject = "test.addorupdate.>"
						description = "Second description"
					}

				assertEquals("Second description", consumerInfo2.config.description)

				// Clean up
				js.manager.deleteConsumer("test_addorupdate_stream", "test_addorupdate_consumer")
				js.manager.deleteStream("test_addorupdate_stream")
			}
		}

	@Test
	fun `it deletes a consumer`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_delete_consumer_stream"
					subject("test.delete.consumer.>")
				}

				// Create consumer
				js.manager.createConsumer("test_delete_consumer_stream") {
					durableName = "test_delete_consumer"
					filterSubject = "test.delete.consumer.>"
				}

				// Delete consumer
				val deleted = js.manager.deleteConsumer("test_delete_consumer_stream", "test_delete_consumer")
				assertTrue(deleted)

				// Clean up
				js.manager.deleteStream("test_delete_consumer_stream")
			}
		}

	@Test
	fun `it pauses and resumes a consumer`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_pause_stream"
					subject("test.pause.>")
				}

				// Create consumer
				js.manager.createConsumer("test_pause_stream") {
					durableName = "test_pause_consumer"
					filterSubject = "test.pause.>"
				}

				// Pause consumer (pause for 1 minute from now)
				val pauseUntil = Clock.System.now() + 1.minutes
				val pauseResponse = js.manager.pauseConsumer("test_pause_stream", "test_pause_consumer", pauseUntil)
				assertTrue(pauseResponse.paused)

				// Resume consumer
				val resumed = js.manager.resumeConsumer("test_pause_stream", "test_pause_consumer")
				assertTrue(resumed)

				// Clean up
				js.manager.deleteConsumer("test_pause_stream", "test_pause_consumer")
				js.manager.deleteStream("test_pause_stream")
			}
		}

	@Test
	fun `it gets consumer info`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_consumer_info_stream"
					subject("test.consumer.info.>")
				}

				// Create consumer
				js.manager.createConsumer("test_consumer_info_stream") {
					durableName = "test_consumer_info"
					filterSubject = "test.consumer.info.>"
				}

				// Get consumer info
				val info = js.manager.getConsumerInfo("test_consumer_info_stream", "test_consumer_info")
				assertNotNull(info)
				assertEquals("test_consumer_info", info.name)

				// Clean up
				js.manager.deleteConsumer("test_consumer_info_stream", "test_consumer_info")
				js.manager.deleteStream("test_consumer_info_stream")
			}
		}

	@Test
	fun `it gets consumer names`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_consumer_names_stream"
					subject("test.consumer.names.>")
				}

				// Create consumers
				js.manager.createConsumer("test_consumer_names_stream") {
					durableName = "consumer1"
					filterSubject = "test.consumer.names.>"
				}
				js.manager.createConsumer("test_consumer_names_stream") {
					durableName = "consumer2"
					filterSubject = "test.consumer.names.>"
				}

				// Get consumer names
				val names = js.manager.getConsumerNames("test_consumer_names_stream")
				assertTrue(names.contains("consumer1"))
				assertTrue(names.contains("consumer2"))

				// Clean up
				js.manager.deleteConsumer("test_consumer_names_stream", "consumer1")
				js.manager.deleteConsumer("test_consumer_names_stream", "consumer2")
				js.manager.deleteStream("test_consumer_names_stream")
			}
		}

	@Test
	fun `it gets all consumers`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_get_consumers_stream"
					subject("test.get.consumers.>")
				}

				// Create consumers
				js.manager.createConsumer("test_get_consumers_stream") {
					durableName = "get_consumer1"
					filterSubject = "test.get.consumers.>"
				}
				js.manager.createConsumer("test_get_consumers_stream") {
					durableName = "get_consumer2"
					filterSubject = "test.get.consumers.>"
				}

				// Get consumers
				val consumers = js.manager.getConsumers("test_get_consumers_stream")
				assertTrue(consumers.any { it.name == "get_consumer1" })
				assertTrue(consumers.any { it.name == "get_consumer2" })

				// Clean up
				js.manager.deleteConsumer("test_get_consumers_stream", "get_consumer1")
				js.manager.deleteConsumer("test_get_consumers_stream", "get_consumer2")
				js.manager.deleteStream("test_get_consumers_stream")
			}
		}

	@Test
	fun `it gets a message by sequence`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_get_message_stream"
					subject("test.get.message.>")
				}

				// Publish message
				val ack = js.publish("test.get.message.1", "test message".encodeToByteArray())

				// Get message
				val messageInfo = js.manager.getMessage("test_get_message_stream", ack.seq)
				assertNotNull(messageInfo)
				assertEquals(ack.seq, messageInfo.sequence)

				// Clean up
				js.manager.deleteStream("test_get_message_stream")
			}
		}

	@Test
	fun `it gets the last message for a subject`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_last_message_stream"
					subject("test.last.message.>")
				}

				// Publish messages
				js.publish("test.last.message.1", "message1".encodeToByteArray())
				val lastAck = js.publish("test.last.message.1", "message2".encodeToByteArray(), headers = mapOf("bing" to listOf("boom")))

				// Get last message
				val messageInfo = js.manager.getLastMessage("test_last_message_stream", "test.last.message.1", direct = false)
				assertNotNull(messageInfo)
				assertEquals(lastAck.seq, messageInfo.sequence)
				assertEquals("message2", messageInfo.data?.decodeToString())
				assertEquals(mapOf("bing" to listOf("boom")), messageInfo.headers)

				// Clean up
				js.manager.deleteStream("test_last_message_stream")
			}
		}

	@Test
	fun `it throws an error when no message found on get from stream`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_last_message_stream"
					subject("test.last.message.>")
				}

				// Publish messages
				js.publish("test.last.message.1", "message1".encodeToByteArray(), headers = mapOf("bing" to listOf("boom")))

				// Get last message
				assertFailsWith<JetStreamApiException> {
					js.manager.getLastMessage("test_last_message_stream", "not.the.right.subject", direct = false)
				}
			}
		}

	@Test
	fun `it deletes a message`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream
				js.manager.createStream {
					name = "test_delete_message_stream"
					subject("test.delete.message.>")
				}

				// Publish message
				val ack = js.publish("test.delete.message.1", "test message".encodeToByteArray())

				// Delete message
				val deleted = js.manager.deleteMessage("test_delete_message_stream", ack.seq, erase = true)
				assertTrue(deleted)

				// Verify message is gone
				val result = runCatching { js.manager.getMessage("test_delete_message_stream", ack.seq) }
				assertTrue(result.isFailure)

				// Clean up
				js.manager.deleteStream("test_delete_message_stream")
			}
		}

	@Test
	fun `it gets a message by sequence using direct-get`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream with direct access enabled
				js.manager.createStream {
					name = "test_direct_get_stream"
					subject("test.direct.get.>")
					allowDirect = true
				}

				// Publish message
				val ack =
					js.publish(
						subject = "test.direct.get.1",
						message = "test message".encodeToByteArray(),
						headers = mapOf("badabing" to listOf("badaboom")),
					)

				// Get message using direct-get API
				val messageInfo = js.manager.getMessage("test_direct_get_stream", ack.seq, direct = true)
				assertNotNull(messageInfo)
				assertEquals(ack.seq, messageInfo.sequence)
				assertEquals("test message", messageInfo.data?.decodeToString())
				assertEquals(
					mapOf(
						"badabing" to listOf("badaboom"),
						"Nats-Stream" to listOf("test_direct_get_stream"),
					),
					messageInfo.headers,
				)

				// Clean up
				js.manager.deleteStream("test_direct_get_stream")
			}
		}

	@Test
	fun `it throws for message not found by sequence when using direct-get`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream with direct access enabled
				js.manager.createStream {
					name = "test_direct_get_stream"
					subject("test.direct.get.>")
					allowDirect = true
				}

				// Publish message
				val ack =
					js.publish(
						subject = "test.direct.get.1",
						message = "test message".encodeToByteArray(),
						headers = mapOf("badabing" to listOf("badaboom")),
					)

				assertFailsWith<JetStreamApiException> {
					js.manager.getMessage("test_direct_get_stream", 4u, direct = true)
				}
			}
		}

	@Test
	fun `it gets the last message for a subject using direct-get`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				// Create stream with direct access enabled
				js.manager.createStream {
					name = "test_direct_last_stream"
					subject("test.direct.last.>")
					allowDirect = true
				}

				// Publish messages
				js.publish("test.direct.last.1", "message1".encodeToByteArray())
				val lastAck = js.publish("test.direct.last.1", "message2".encodeToByteArray())

				// Get last message using direct-get API
				val messageInfo = js.manager.getLastMessage("test_direct_last_stream", "test.direct.last.1", direct = true)
				assertNotNull(messageInfo)
				assertEquals(lastAck.seq, messageInfo.sequence)

				// Clean up
				js.manager.deleteStream("test_direct_last_stream")
			}
		}
}

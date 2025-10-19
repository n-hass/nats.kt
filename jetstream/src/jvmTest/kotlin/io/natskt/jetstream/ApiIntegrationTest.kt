package io.natskt.jetstream

import harness.NatsServerHarness
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.natskt.NatsClient
import io.natskt.jetstream.api.JetStreamApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class ApiIntegrationTest {
	@Test
	fun `it gets a stream not found error for something not found`() =
		NatsServerHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

			val s = js.stream("test_stream")
			assertIs<JetStreamApiException>(s.updateStreamInfo().exceptionOrNull())
		}

	@Test
	fun `it creates a stream with given configuration`() =
		NatsServerHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

			val s =
				js.createStream {
					name = "abc"
					subject("ABC")
					subject("124.>")
					source {
						domain = "cloud"
						external {
							api = "ee"
						}
					}
				}

			val initialInfo = s.info.value
			assertNotNull(initialInfo)

			assertEquals(initialInfo.config, s.updateStreamInfo().getOrNull()?.config)
		}

	@Test
	fun `it creates a consumer`() =
		NatsServerHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

			val s =
				js.createStream {
					name = "test_stream_for_basic_consumer"
					subject("test.basic_consumer")
				}

			val consumer =
				s.createPullConsumer {
					durableName = "consumer1"
					filterSubjects =
						mutableListOf(
							"test.basic_consumer.hi.consumer1",
						)
				}

// 			c.publish("test.hi.consumer1", "hi1".encodeToByteArray())

			val initialInfo = consumer.info.value
			assertNotNull(initialInfo)

			assertEquals(initialInfo.config, consumer.updateConsumerInfo().getOrNull()?.config)
		}

	@Test
	fun `pull consumer can fetch`() =
		NatsServerHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

			val s =
				js.createStream {
					name = "test_stream_for_basic_consumer"
					subject("test.basic_consumer.>")
				}

			val consumer =
				s.createPullConsumer {
					durableName = "consumer1"
					filterSubjects =
						mutableListOf(
							"test.basic_consumer.hi.consumer1",
						)
				}

			c.publish("test.basic_consumer.foo", "hi1".encodeToByteArray())

			assertEquals(0, consumer.fetch(1, expires = 1.seconds).size, message = "consumer should not return any messages")

			eventually(eventuallyConfig { duration = 1.seconds }) {
				assertEquals(
					1,
					s
						.updateStreamInfo()
						.getOrThrow()
						.state.messages,
					"stream should contain a message",
				)
			}

			c.publish("test.basic_consumer.hi.consumer1", "hi to consumer".encodeToByteArray())

			eventually(eventuallyConfig { duration = 1.seconds }) {
				assertEquals(
					2,
					s
						.updateStreamInfo()
						.getOrThrow()
						.state.messages,
					"stream should contain both messages",
				)
			}

			val messages = consumer.fetch(1, noWait = true)

			assertEquals(1, messages.size, "consumer fetch should return 1 message")

			assertEquals("hi to consumer", messages.single().data?.decodeToString())
		}
}

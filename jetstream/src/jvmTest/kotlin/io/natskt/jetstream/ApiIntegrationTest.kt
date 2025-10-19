package io.natskt.jetstream

import harness.NatsServerHarness
import io.natskt.NatsClient
import io.natskt.jetstream.api.JetStreamApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

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
							"test.hi.consumer1",
						)
				}

// 			c.publish("test.hi.consumer1", "hi1".encodeToByteArray())

			val initialInfo = consumer.info.value
			assertNotNull(initialInfo)

			assertEquals(initialInfo.config, consumer.updateConsumerInfo().getOrNull()?.config)
		}
}

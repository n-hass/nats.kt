package io.natskt.jetstream.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.jetstream.api.JetStreamApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ApiIntegrationTest {
	@Test
	fun `it gets a stream not found error for something not found`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val s = js.stream("test_stream")
				assertIs<JetStreamApiException>(s.updateStreamInfo().exceptionOrNull())
			}
		}

	@Test
	fun `it creates a stream with given configuration`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val s =
					js.manager.createStream {
						name = "abc"
						subject("ABC")
						subject("124.>")
						source {
							name = "stuff"
							external {
								apiPrefix = "ee"
							}
						}
					}

				val initialInfo = s.info.value
				assertNotNull(initialInfo)

				assertEquals(initialInfo.config, s.updateStreamInfo().getOrNull()?.config)
			}
		}

	@Test
	fun `it creates a consumer`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val s =
					js.manager.createStream {
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

				val initialInfo = consumer.info.value
				assertNotNull(initialInfo)

				assertEquals(initialInfo.config, consumer.updateConsumerInfo().getOrNull()?.config)
			}
		}
}

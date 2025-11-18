package io.natskt.jetstream.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.natskt.NatsClient
import io.natskt.api.Message
import io.natskt.api.internal.InternalNatsApi
import io.natskt.jetstream.JetStreamClient
import io.natskt.jetstream.api.AckPolicy
import io.natskt.jetstream.api.ConsumerConfig
import io.natskt.jetstream.api.DeliverPolicy
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.internal.PushConsumerImpl
import io.natskt.jetstream.internal.createOrUpdateConsumer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ApiIntegrationTest {
	@Test
	fun `it gets a stream not found error for something not found`() =
		RemoteNatsHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

			val s = js.stream("test_stream")
			assertIs<JetStreamApiException>(s.updateStreamInfo().exceptionOrNull())
		}

	@Test
	fun `it creates a stream with given configuration`() =
		RemoteNatsHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

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

	@Test
	fun `it creates a consumer`() =
		RemoteNatsHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

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

// 			c.publish("test.hi.consumer1", "hi1".encodeToByteArray())

			val initialInfo = consumer.info.value
			assertNotNull(initialInfo)

			assertEquals(initialInfo.config, consumer.updateConsumerInfo().getOrNull()?.config)
		}

	@Test
	fun `pull consumer can fetch`() =
		RemoteNatsHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

			val s =
				js.manager.createStream {
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

			eventually(eventuallyConfig { duration = 500.milliseconds }) {
				assertEquals(
					1u,
					s
						.updateStreamInfo()
						.getOrThrow()
						.state.messages,
					"stream should contain a message",
				)
			}

			c.publish("test.basic_consumer.hi.consumer1", "hi to consumer".encodeToByteArray())

			eventually(eventuallyConfig { duration = 500.milliseconds }) {
				assertEquals(
					2u,
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
			messages.forEach { it.ackWait() }
		}

	@Test
	fun `pull consumer can fetch continuously`() =
		RemoteNatsHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

			val s =
				js.manager.createStream {
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
					ackPolicy = AckPolicy.None
				}

			val received = mutableListOf<Message>()
			val x =
				launch {
					withTimeoutOrNull(5.seconds) {
						while (received.size < 5) {
							val fetched = consumer.fetch(1)
							received.addAll(fetched)
						}
					}
				}

			c.publish("test.basic_consumer.hi.consumer1", "1".encodeToByteArray())
			c.publish("test.basic_consumer.hi.consumer1", "2".encodeToByteArray())
			c.publish("test.basic_consumer.hi.consumer1", "3".encodeToByteArray())
			c.publish("test.basic_consumer.hi.consumer1", "4".encodeToByteArray())
			c.publish("test.basic_consumer.hi.consumer1", "5".encodeToByteArray())

			x.join()

			assertEquals(5, received.size)
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `push consumer receives messages`() =
		RemoteNatsHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

			js.manager.createStream {
				name = "push_stream_binding"
				subject("push.binding")
			}

			val consumerName = "push_consumer"
			val deliverSubject = c.nextInbox()

			val createdInfo =
				js
					.createOrUpdateConsumer(
						"push_stream_binding",
						ConsumerConfig(
							durableName = consumerName,
							deliverSubject = deliverSubject,
							filterSubject = "push.binding",
							deliverPolicy = DeliverPolicy.All,
							ackPolicy = AckPolicy.None,
							flowControl = true,
							idleHeartbeat = 5.seconds,
						),
					).getOrThrow()

			val pushConsumer = js.stream("push_stream_binding").pushConsumer(consumerName) as PushConsumerImpl

			val received = mutableListOf<String>()
			val job =
				launch {
					withTimeout(5.seconds) {
						pushConsumer.messages.take(2).collect {
							received += it.data!!.decodeToString()
						}
					}
				}
			job.start()

			delay(1)
			c.publish("push.binding", "alpha".encodeToByteArray())
			c.publish("push.binding", "beta".encodeToByteArray())

			job.join()

			assertEquals(listOf("alpha", "beta"), received)
			assertEquals(createdInfo.config.deliverSubject, pushConsumer.subscription.subject.raw)

			pushConsumer.close()
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `push consumer redelivers when not acked`() =
		RemoteNatsHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

			js.manager.createStream {
				name = "push_ack_stream"
				subject("push.ack")
			}

			val consumerName = "push_ack_consumer"
			val deliverSubject = c.nextInbox()

			js
				.createOrUpdateConsumer(
					"push_ack_stream",
					ConsumerConfig(
						durableName = consumerName,
						deliverSubject = deliverSubject,
						filterSubject = "push.ack",
						deliverPolicy = DeliverPolicy.All,
						ackPolicy = AckPolicy.Explicit,
						ackWait = 500.milliseconds, // 500ms
						maxDeliver = 5,
						flowControl = true,
						idleHeartbeat = 5.seconds,
					),
				).getOrThrow()

			val pushConsumer = js.stream("push_ack_stream").pushConsumer(consumerName)

			val deliveries = mutableListOf<String>()
			val job =
				launch {
					withTimeout(5.seconds) {
						pushConsumer.messages.take(2).collect { msg ->
							deliveries += msg.data!!.decodeToString()
							if (deliveries.size == 2) {
								msg.ack()
							}
						}
					}
				}

			delay(100.milliseconds)
			c.publish("push.ack", "needs ack".encodeToByteArray())

			job.join()

			assertEquals(listOf("needs ack", "needs ack"), deliveries)

			pushConsumer.close()
		}
}

package io.natskt.jetstream.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.natskt.api.Message
import io.natskt.api.internal.InternalNatsApi
import io.natskt.jetstream.api.AckPolicy
import io.natskt.jetstream.api.ConsumerConfig
import io.natskt.jetstream.api.DeliverPolicy
import io.natskt.jetstream.api.consumer.PullConsumer
import io.natskt.jetstream.api.consumer.SubscribeOptions
import io.natskt.jetstream.internal.PushConsumerImpl
import io.natskt.jetstream.internal.createOrUpdateConsumer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConsumerIntegrationTest {
	@OptIn(InternalNatsApi::class)
	@Test
	fun `push consumer receives messages`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { c, js ->
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
				val deferred = CompletableDeferred(Unit)
				val job =
					launch {
						withTimeout(5.seconds) {
							pushConsumer.messages
								.take(2)
								.onStart {
									deferred.complete(Unit)
								}.collect {
									received += it.data!!.decodeToString()
								}
						}
					}
				job.start()

				deferred.await()
				delay(100)
				js.publish("push.binding", "alpha".encodeToByteArray())
				js.publish("push.binding", "beta".encodeToByteArray())

				job.join()

				assertEquals(listOf("alpha", "beta"), received)
				assertEquals(createdInfo.config.deliverSubject, pushConsumer.subscription.subject.raw)

				ignoreClosedWrite { pushConsumer.close() }
			}
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `subscribe create or update binds consumer and receives messages`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { c, js ->
				js.manager.createStream {
					name = "subscribe_create_stream"
					subject("subscribe.create")
				}

				val consumerName = "subscribe_create_consumer"
				val deliverSubject = c.nextInbox()

				val consumer =
					js.subscribe(
						"subscribe.create",
						SubscribeOptions.CreateOrUpdate(
							streamName = "subscribe_create_stream",
							consumerName = consumerName,
							config =
								ConsumerConfig(
									durableName = consumerName,
									deliverSubject = deliverSubject,
									filterSubject = "subscribe.create",
									deliverPolicy = DeliverPolicy.All,
									ackPolicy = AckPolicy.None,
									flowControl = true,
									idleHeartbeat = 5.seconds,
								),
						),
					) as PushConsumerImpl

				val received = mutableListOf<String>()
				val deferred = CompletableDeferred(Unit)
				val job =
					launch {
						withTimeout(5.seconds) {
							consumer.messages.take(2).onStart { deferred.complete(Unit) }.collect {
								received += it.data!!.decodeToString()
							}
						}
					}
				job.start()

				deferred.await()
				delay(100)
				c.publish("subscribe.create", "create-one".encodeToByteArray())
				c.publish("subscribe.create", "create-two".encodeToByteArray())

				job.join()

				assertEquals(listOf("create-one", "create-two"), received)
				assertEquals(consumerName, consumer.info.value?.name)
				assertEquals(deliverSubject, consumer.subscription.subject.raw)

				ignoreClosedWrite { consumer.close() }
			}
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `subscribe attach binds existing ephemeral consumer and receives messages`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { c, js ->
				js.manager.createStream {
					name = "subscribe_attach_stream"
					subject("subscribe.>")
				}

				val deliverSubject = c.nextInbox()

				val createdInfo =
					js
						.createOrUpdateConsumer(
							"subscribe_attach_stream",
							ConsumerConfig(
								deliverSubject = deliverSubject,
								filterSubjects = listOf("subscribe.a", "subscribe.b.*"),
								deliverPolicy = DeliverPolicy.All,
								ackPolicy = AckPolicy.None,
								flowControl = true,
								idleHeartbeat = 5.seconds,
							),
						).getOrThrow()

				println("created consumer name is ${createdInfo.name}")

				val consumer =
					js.subscribe(
						"subscribe.attach",
						SubscribeOptions.Attach(streamName = "subscribe_attach_stream", consumerName = createdInfo.name),
					) as PushConsumerImpl

				val received = mutableListOf<String>()
				val deferred = CompletableDeferred(Unit)
				val job =
					launch {
						withTimeout(5.seconds) {
							consumer.messages
								.take(2)
								.onStart {
									deferred.complete(Unit)
								}.collect {
									received += it.data!!.decodeToString()
								}
						}
					}
				job.start()

				deferred.await()
				delay(100)
				c.publish("subscribe.a", "attach-one".encodeToByteArray())
				c.publish("subscribe.b.attach", "attach-two".encodeToByteArray())
				c.publish("subscribe.c", "attach-three".encodeToByteArray())

				job.join()

				assertEquals(listOf("attach-one", "attach-two"), received)
				assertEquals(createdInfo.config.deliverSubject, consumer.subscription.subject.raw)

				ignoreClosedWrite { consumer.close() }
			}
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `subscribe create or update returns pull consumer`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { c, js ->
				js.manager.createStream {
					name = "subscribe_pull_create_stream"
					subject("subscribe.pull.create")
				}

				val consumerName = "subscribe_pull_create_consumer"

				val consumer =
					js.subscribe(
						"subscribe.pull.create",
						SubscribeOptions.CreateOrUpdate(
							streamName = "subscribe_pull_create_stream",
							consumerName = consumerName,
							config =
								ConsumerConfig(),
						),
					)

				val pullConsumer = assertIs<PullConsumer>(consumer)

				js.publish("subscribe.pull.create", "pull-create-one".encodeToByteArray())
				js.publish("subscribe.pull.create", "pull-create-two".encodeToByteArray())

				val payloads = collectPullMessages(pullConsumer, expected = 2)

				assertEquals(listOf("pull-create-one", "pull-create-two"), payloads)
			}
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `subscribe attach returns pull consumer`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { c, js ->
				js.manager.createStream {
					name = "subscribe_pull_attach_stream"
					subject("subscribe.pull.attach")
				}

				val consumerName = "subscribe_pull_attach_consumer"

				js
					.createOrUpdateConsumer(
						"subscribe_pull_attach_stream",
						ConsumerConfig(
							durableName = consumerName,
							filterSubject = "subscribe.pull.attach",
							deliverPolicy = DeliverPolicy.All,
							ackPolicy = AckPolicy.None,
						),
					).getOrThrow()

				val consumer =
					js.subscribe(
						SubscribeOptions.Attach(streamName = "subscribe_pull_attach_stream", consumerName = consumerName),
					)

				val pullConsumer = assertIs<PullConsumer>(consumer)

				js.publish("subscribe.pull.attach", "pull-attach-one".encodeToByteArray())
				js.publish("subscribe.pull.attach", "pull-attach-two".encodeToByteArray())

				val payloads = collectPullMessages(pullConsumer, expected = 2)

				assertEquals(listOf("pull-attach-one", "pull-attach-two"), payloads)
				assertEquals(consumerName, pullConsumer.info.value?.name)
				assertEquals(
					null,
					pullConsumer.info.value
						?.config
						?.deliverSubject,
				)
			}
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `subscribe attach ignores requested subject when mismatch`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { c, js ->
				js.manager.createStream {
					name = "subscribe_attach_subject_stream"
					subject("subscribe.attach.subject.*")
				}

				val consumerName = "subscribe_attach_subject_consumer"
				val deliverSubject = c.nextInbox()

				js
					.createOrUpdateConsumer(
						"subscribe_attach_subject_stream",
						ConsumerConfig(
							durableName = consumerName,
							deliverSubject = deliverSubject,
							filterSubject = "subscribe.attach.subject.correct",
							deliverPolicy = DeliverPolicy.All,
							ackPolicy = AckPolicy.None,
							flowControl = true,
							idleHeartbeat = 5.seconds,
						),
					).getOrThrow()

				val consumer =
					js.subscribe(
						"subscribe.attach.subject.mismatch",
						SubscribeOptions.Attach(
							streamName = "subscribe_attach_subject_stream",
							consumerName = consumerName,
						),
					) as PushConsumerImpl

				val received = mutableListOf<String>()
				val job =
					launch {
						withTimeout(5.seconds) {
							consumer.messages.take(1).collect {
								received += it.data!!.decodeToString()
							}
						}
					}
				job.start()

				js.publish("subscribe.attach.subject.mismatch", "ignored".encodeToByteArray())
				delay(200)
				js.publish("subscribe.attach.subject.correct", "respected-filter".encodeToByteArray())

				job.join()

				assertEquals(listOf("respected-filter"), received)
				assertEquals(deliverSubject, consumer.subscription.subject.raw)

				ignoreClosedWrite { consumer.close() }
			}
		}

	@Test
	fun `pull consumer can fetch`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { c, js ->
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

				js.publish(subject = "test.basic_consumer.foo", message = "hi1".encodeToByteArray())

				assertEquals(0, consumer.fetch(1, expires = 1.seconds).size, message = "consumer should not return any messages")

				eventually(
					eventuallyConfig {
						duration = 5.seconds
						interval = 250.milliseconds
					},
				) {
					assertEquals(
						1u,
						s
							.updateStreamInfo()
							.getOrThrow()
							.state.messages,
						"stream should contain a message",
					)
				}

				js.publish("test.basic_consumer.hi.consumer1", "hi to consumer".encodeToByteArray())

				eventually(
					eventuallyConfig {
						duration = 5.seconds
						interval = 250.milliseconds
					},
				) {
					assertEquals(
						2u,
						s
							.updateStreamInfo()
							.getOrThrow()
							.state.messages,
						"stream should contain both messages",
					)
				}

				val messages = consumer.fetch(1)

				assertEquals(1, messages.size, "consumer fetch should return 1 message")

				assertEquals("hi to consumer", messages.single().data?.decodeToString())
				messages.forEach { it.ackWait() }
			}
		}

	@Test
	fun `pull consumer can fetch continuously`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { c, js ->
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

				js.publish("test.basic_consumer.hi.consumer1", "1".encodeToByteArray())
				js.publish("test.basic_consumer.hi.consumer1", "2".encodeToByteArray())
				js.publish("test.basic_consumer.hi.consumer1", "3".encodeToByteArray())
				js.publish("test.basic_consumer.hi.consumer1", "4".encodeToByteArray())
				js.publish("test.basic_consumer.hi.consumer1", "5".encodeToByteArray())

				x.join()

				assertEquals(5, received.size)
			}
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `push consumer redelivers when not acked`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { c, js ->
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
				val deferred = CompletableDeferred(Unit)
				val job =
					launch {
						withTimeout(5.seconds) {
							pushConsumer.messages
								.take(2)
								.onStart {
									deferred.complete(Unit)
								}.collect { msg ->
									deliveries += msg.data!!.decodeToString()
									if (deliveries.size == 2) {
										msg.ack()
									}
								}
						}
					}

				deferred.await()
				delay(100)
				js.publish("push.ack", "needs ack".encodeToByteArray())

				job.join()

				assertEquals(listOf("needs ack", "needs ack"), deliveries)

				ignoreClosedWrite { pushConsumer.close() }
			}
		}

	private suspend fun collectPullMessages(
		consumer: PullConsumer,
		expected: Int,
	): List<String> {
		val results = mutableListOf<String>()
		withTimeout(5.seconds) {
			while (results.size < expected) {
				val fetched = consumer.fetch(expected, expires = 1.seconds)
				if (fetched.isEmpty()) {
					delay(10)
					continue
				}
				results += fetched.map { it.data!!.decodeToString() }
			}
		}
		return results
	}
}

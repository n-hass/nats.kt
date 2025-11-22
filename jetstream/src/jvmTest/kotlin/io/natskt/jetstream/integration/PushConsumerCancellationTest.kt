package io.natskt.jetstream.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.api.internal.InternalNatsApi
import io.natskt.jetstream.api.AckPolicy
import io.natskt.jetstream.api.ConsumerConfig
import io.natskt.jetstream.api.DeliverPolicy
import io.natskt.jetstream.api.consumer.JetStreamHeartbeatException
import io.natskt.jetstream.internal.PushConsumerImpl
import io.natskt.jetstream.internal.createOrUpdateConsumer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PushConsumerCancellationTest {
	@OptIn(InternalNatsApi::class)
	@Ignore
	@Test
	fun `push consumer is cancelled when no heartbeats`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { c, js ->
				js.manager.createStream {
					name = "push_stream_binding"
					subject("push.binding")
				}

				val consumerName = "dead_consumer"
				val deliverSubject = c.nextInbox()

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

				// force this lower to trigger it to fail
				pushConsumer.heartbeatInterval = 1.seconds

				val received = mutableListOf<String>()
				val job =
					launch {
						withTimeout(15.seconds) {
							try {
								pushConsumer.messages.collect {
									received += it.data!!.decodeToString()
								}
							} catch (e: Exception) {
								assertIs<JetStreamHeartbeatException>(e)
							}
						}
					}
				job.start()

				delay(50)
				js.client.publish("push.binding", "t".encodeToByteArray())
				job.invokeOnCompletion {
					assertEquals(listOf("t"), received)
				}
			}
		}

	@OptIn(InternalNatsApi::class)
	@Ignore
	@Test
	fun `push consumer is NOT cancelled with expected heartbeats`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { c, js ->
				js.manager.createStream {
					name = "push_stream_binding"
					subject("push.binding")
				}

				val consumerName = "push_consumer"
				val deliverSubject = c.nextInbox()

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
							idleHeartbeat = 250.milliseconds,
						),
					).getOrThrow()

				val pushConsumer = js.stream("push_stream_binding").pushConsumer(consumerName) as PushConsumerImpl

				// force this lower to trigger it to fail
				pushConsumer.heartbeatInterval = 500.milliseconds

				val received = mutableListOf<String>()
				val job =
					launch {
						withTimeout(15.seconds) {
							try {
								pushConsumer.messages.collect {
									received += it.data!!.decodeToString()
								}
							} catch (e: Exception) {
								assertIs<TimeoutCancellationException>(e)
							}
						}
					}
				job.start()

				delay(50)
				js.client.publish("push.binding", "t".encodeToByteArray())
				delay(5000)
				js.client.publish("push.binding", "b".encodeToByteArray())
				js.client.publish("push.binding", "8".encodeToByteArray())
				job.invokeOnCompletion {
					assertEquals(listOf("t", "b", "8"), received)
				}
			}
		}
}

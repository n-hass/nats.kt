package io.natskt.jetstream.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.jetstream.api.AckPolicy
import io.natskt.jetstream.api.consumer.ConsumeOptions
import io.natskt.jetstream.api.consumer.PullConsumer
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PullConsumerConsumeTest {
	@Test
	fun `consume drains a stream across multiple pull windows`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				js.manager.createStream {
					name = "consume_drain"
					subject("consume_drain.>")
				}
				val total = 1000
				repeat(total) { i ->
					js.publish("consume_drain.msg", "m$i".encodeToByteArray())
				}

				val consumer: PullConsumer =
					js.stream("consume_drain").createPullConsumer {
						durableName = "consume_drain_consumer"
						ackPolicy = AckPolicy.None
						filterSubject = "consume_drain.>"
					}

				val received =
					withTimeout(30.seconds) {
						consumer
							.consume(ConsumeOptions(batch = 200))
							.take(total)
							.toList()
					}

				assertEquals(total, received.size, "all messages must be delivered")
				val payloads = received.map { it.data!!.decodeToString() }
				assertEquals((0 until total).map { "m$it" }, payloads, "messages must be in publish order")
			}
		}

	@Test
	fun `consume terminates when collector cancels`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				js.manager.createStream {
					name = "consume_cancel"
					subject("consume_cancel.>")
				}
				repeat(50) { i ->
					js.publish("consume_cancel.msg", "x$i".encodeToByteArray())
				}

				val consumer: PullConsumer =
					js.stream("consume_cancel").createPullConsumer {
						durableName = "consume_cancel_consumer"
						ackPolicy = AckPolicy.None
						filterSubject = "consume_cancel.>"
					}

				val received = mutableListOf<String>()
				val job =
					launch {
						consumer.consume(ConsumeOptions(batch = 10)).take(5).collect { msg ->
							received += msg.data!!.decodeToString()
						}
					}
				job.join()

				assertEquals(5, received.size)
				assertTrue(received[0] == "x0", "expected first message but got ${received[0]}")
			}
		}
}

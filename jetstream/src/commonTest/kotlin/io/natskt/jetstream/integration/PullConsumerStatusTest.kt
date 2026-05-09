package io.natskt.jetstream.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.jetstream.api.AckPolicy
import io.natskt.jetstream.api.JetStreamConsumerStateException
import io.natskt.jetstream.api.consumer.PullConsumer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PullConsumerStatusTest {
	@Test
	fun `fetch with noWait on empty stream returns empty list`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				js.manager.createStream {
					name = "pull_status_404"
					subject("pull_status_404.>")
				}
				val consumer: PullConsumer =
					js.stream("pull_status_404").createPullConsumer {
						durableName = "consumer_404"
						ackPolicy = AckPolicy.None
						filterSubject = "pull_status_404.>"
					}

				val messages = consumer.fetch(batch = 5, expires = 2.seconds, noWait = true)
				assertTrue(messages.isEmpty(), "noWait fetch on empty stream should return empty")
			}
		}

	@Test
	fun `fetch on empty stream completes after expires (408)`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				js.manager.createStream {
					name = "pull_status_408"
					subject("pull_status_408.>")
				}
				val consumer: PullConsumer =
					js.stream("pull_status_408").createPullConsumer {
						durableName = "consumer_408"
						ackPolicy = AckPolicy.None
						filterSubject = "pull_status_408.>"
					}

				val messages = consumer.fetch(batch = 5, expires = 500.milliseconds)
				assertTrue(messages.isEmpty(), "fetch should complete with empty list after expires elapses")
			}
		}

	@Test
	fun `fetch raises JetStreamConsumerStateException when consumer is deleted mid-flight`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				js.manager.createStream {
					name = "pull_status_409"
					subject("pull_status_409.>")
				}

				val consumer: PullConsumer =
					js.stream("pull_status_409").createPullConsumer {
						durableName = "consumer_409"
						ackPolicy = AckPolicy.None
						filterSubject = "pull_status_409.>"
					}

				// supervisorScope so a thrown JetStreamConsumerStateException doesn't
				// cancel the test coroutine before we can inspect it.
				val captured = CompletableDeferred<Throwable>()
				supervisorScope {
					launch {
						try {
							consumer.fetch(batch = 10, expires = 5.seconds)
							captured.complete(IllegalStateException("fetch returned without throwing"))
						} catch (t: Throwable) {
							captured.complete(t)
						}
					}

					delay(200)
					js.manager.deleteConsumer("pull_status_409", "consumer_409")
				}

				val ex = captured.await()
				assertNotNull(ex, "expected an exception")
				assertTrue(
					ex is JetStreamConsumerStateException,
					"expected JetStreamConsumerStateException but got ${ex::class.simpleName}: $ex",
				)
				assertEquals(409, ex.code)
			}
		}
}

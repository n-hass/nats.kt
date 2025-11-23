package io.natskt.jetstream.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.jetstream.api.kv.KeyValueEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class KvIntegrationTest {
	@Test
	fun `it creates a bucket`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket =
					js.keyValueManager.create {
						name = "MyCoolBucket"
					}

				assertEquals("MyCoolBucket", bucket.config!!.bucket)
				assertEquals(1u, bucket.config!!.history)
				assertEquals(Duration.ZERO, bucket.config!!.ttl)
				assertEquals(-1, bucket.config!!.maxValueSize)
				assertEquals(-1, bucket.config!!.maxBytes)
			}
		}

	@Test
	fun `it gets an existing bucket`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val createResult =
					js.keyValueManager.create {
						name = "MyCoolBucketAgain"
					}

				val getResult = js.keyValueManager.get("MyCoolBucketAgain")

				assertEquals(createResult.config, getResult.config)
			}
		}

	@Test
	fun `it puts on a bucket`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				js.keyValueManager.create {
					name = "MyCoolBucket"
				}

				val bucket = js.keyValue("MyCoolBucket")

				bucket.put("a.b", "test".encodeToByteArray())
			}
		}

	@Test
	fun `it gets on a bucket`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				js.keyValueManager.create {
					name = "MyCoolBucket"
				}

				val bucket = js.keyValue("MyCoolBucket")

				bucket.put("a.b", "test1".encodeToByteArray())

				val entry = bucket.get("a.b")

				assertEquals("test1", entry.value.decodeToString())
			}
		}

	@Test
	fun `it gets a past value from a bucket`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				js.keyValueManager.create {
					name = "Foo"
					history = 3
				}

				val bucket = js.keyValue("Foo")

				bucket.put("a.b", "test1".encodeToByteArray())
				bucket.put("a.b", "test2".encodeToByteArray())
				bucket.put("a.b", "test3".encodeToByteArray())

				assertEquals("test1", bucket.get("a.b", revision = 1u).value.decodeToString())
				assertEquals("test2", bucket.get("a.b", revision = 2u).value.decodeToString())
				assertEquals("test3", bucket.get("a.b", revision = 3u).value.decodeToString())
			}
		}

	@Test
	fun `it watches for new values`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket =
					js.keyValueManager.create {
						name = "Foo"
					}

				val first = CompletableDeferred<String>()
				val updates = mutableListOf<KeyValueEntry>()
				val job =
					launch {
						withTimeout(3.seconds) {
							bucket.watch("watching").take(3).collect {
								first.complete(it.value.decodeToString())
								updates.add(it)
							}
						}
					}
				job.start()

				bucket.put("watching", "test1".encodeToByteArray())
				assertEquals("test1", first.await())
				bucket.put("watching", "test2".encodeToByteArray())
				assertEquals("test2", bucket.get("watching").value.decodeToString())
				bucket.put("watching", "test3".encodeToByteArray())
				assertEquals("test3", bucket.get("watching").value.decodeToString())

				job.join()
				assertTrue("no KV updates received") { updates.isNotEmpty() }
				assertEquals(3, updates.size)

				var i = 1
				for (entry in updates) {
					assertEquals(i.toULong(), entry.revision, "revision incorrect. full: $updates")
					assertEquals("test$i", entry.value.decodeToString())
					i++
				}
			}
		}

	@Test
	fun `watch returns the latest value on start`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket =
					js.keyValueManager.create {
						name = "Foo"
					}

				bucket.put("watching", "test1".encodeToByteArray())
				assertEquals(
					"test1",
					bucket
						.watch("watching")
						.take(1)
						.toList()
						.first()
						.value
						.decodeToString(),
				)
				bucket.put("watching", "test2".encodeToByteArray())
				assertEquals(
					"test2",
					bucket
						.watch("watching")
						.take(1)
						.toList()
						.first()
						.value
						.decodeToString(),
				)
			}
		}

	@Test
	fun `it lists bucket keys`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket =
					js.keyValueManager.create {
						name = "NEON"
					}

				bucket.put("b", "1".encodeToByteArray())
				bucket.put("b.c", "2".encodeToByteArray())
				bucket.put("b.d.f", "3".encodeToByteArray())
				bucket.put("c.3p", "1209".encodeToByteArray())

				assertEquals(setOf("b", "b.c", "b.d.f", "c.3p"), bucket.keys().toSet())
				bucket.put("a", "k".encodeToByteArray())
				assertEquals(setOf("a", "b", "b.c", "b.d.f", "c.3p"), bucket.keys().toSet())
			}
		}
}

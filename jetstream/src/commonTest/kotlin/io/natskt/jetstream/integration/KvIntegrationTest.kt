package io.natskt.jetstream.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.kv.KeyValueEntry
import io.natskt.jetstream.api.kv.KeyValueOperation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class KvIntegrationTest {
	var counter = 1

	private fun bucketName(base: String): String = "${base}_${counter++}"

	@Test
	fun `it creates a bucket`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("MyCoolBucket")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				assertEquals(bucketName, bucket.config!!.bucket)
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
				val bucketName = bucketName("MyCoolBucketAgain")
				val createResult =
					js.keyValueManager.create {
						name = bucketName
					}

				val getResult = js.keyValueManager.get(bucketName)

				assertEquals(createResult.config, getResult.config)
			}
		}

	@Test
	fun `it puts on a bucket`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("MyCoolBucket")
				js.keyValueManager.create {
					name = bucketName
				}

				val bucket = js.keyValue(bucketName)

				bucket.put("a.b", "test".encodeToByteArray())
			}
		}

	@Test
	fun `it gets on a bucket`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("MyCoolBucket")
				js.keyValueManager.create {
					name = bucketName
				}

				val bucket = js.keyValue(bucketName)

				bucket.put("a.b", "test1".encodeToByteArray())

				val entry = bucket.get("a.b")

				assertEquals("test1", entry.value.decodeToString())
			}
		}

	@Test
	fun `it gets a past value from a bucket`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("Foo")
				js.keyValueManager.create {
					name = bucketName
					history = 3
				}

				val bucket = js.keyValue(bucketName)

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
				val bucketName = bucketName("Foo")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
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
				val bucketName = bucketName("Foo")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
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
				val bucketName = bucketName("NEON")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
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

	@Test
	fun `it creates keys only once unless deleted`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("CREATE")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				val firstRevision = bucket.create("alpha", "foo".encodeToByteArray())
				assertEquals("foo", bucket.get("alpha").value.decodeToString())

				assertFailsWith<JetStreamApiException> {
					bucket.create("alpha", "bar".encodeToByteArray())
				}

				val deleteRevision = bucket.delete("alpha", lastRevision = firstRevision)
				assertEquals(KeyValueOperation.Delete, bucket.get("alpha").operation)
				assertTrue(deleteRevision > firstRevision)

				val recreatedRevision = bucket.create("alpha", "bar".encodeToByteArray())
				assertTrue(recreatedRevision > deleteRevision)
				assertEquals("bar", bucket.get("alpha").value.decodeToString())
			}
		}

	@Test
	fun `it updates values when revision matches`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("UPDATE")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				val initial = bucket.put("alpha", "foo".encodeToByteArray())
				val updated = bucket.update("alpha", "bar".encodeToByteArray(), initial)
				assertTrue(updated > initial)
				assertEquals("bar", bucket.get("alpha").value.decodeToString())

				assertFailsWith<JetStreamApiException> {
					bucket.update("alpha", "baz".encodeToByteArray(), initial)
				}
			}
		}

	@Test
	fun `it deletes keys with successful revision check`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("DELETE")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				val initial = bucket.put("alpha", "foo".encodeToByteArray())
				val deleteRevision = bucket.delete("alpha", lastRevision = initial)

				val tombstone = bucket.get("alpha")
				assertEquals(KeyValueOperation.Delete, tombstone.operation)
				assertEquals(deleteRevision, tombstone.revision)

				assertFailsWith<JetStreamApiException> {
					bucket.delete("alpha", lastRevision = initial)
				}
			}
		}

	@Test
	fun `it fails to delete key with failed revision check`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("DELETE")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				val initial = bucket.put("alpha", "foo".encodeToByteArray())
				bucket.put("alpha", "bar".encodeToByteArray())

				assertFailsWith<JetStreamApiException> {
					bucket.delete("alpha", lastRevision = initial)
				}

				assertEquals("bar", bucket.get("alpha").value.decodeToString())
			}
		}

	@Test
	fun `it deletes keys without revision checks`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("DELETE2")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				val initial = bucket.put("alpha", "foo".encodeToByteArray())
				val second = bucket.put("alpha", "arst".encodeToByteArray())
				val deleteRevision = bucket.delete("alpha")

				val tombstone = bucket.get("alpha")
				assertEquals(KeyValueOperation.Delete, tombstone.operation)
				assertEquals(deleteRevision, tombstone.revision)

				assertFailsWith<JetStreamApiException> {
					bucket.delete("alpha", lastRevision = initial)
				}
			}
		}

	@Test
	fun `it purges keys and records a marker`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("PURGE")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				bucket.put("alpha", "foo".encodeToByteArray())
				bucket.put("alpha", "oooope".encodeToByteArray())
				val purgeRevision = bucket.purge("alpha")

				val purgeMarker = bucket.get("alpha")
				assertEquals(KeyValueOperation.Purge, purgeMarker.operation)
				assertEquals(purgeRevision, purgeMarker.revision)
				assertContentEquals(byteArrayOf(), purgeMarker.value)

				val recreated = bucket.create("alpha", "bar".encodeToByteArray())
				assertTrue(recreated > purgeRevision)
				assertEquals("bar", bucket.get("alpha").value.decodeToString())
			}
		}
}

package io.natskt.jetstream.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.KvKeyNotFoundException
import io.natskt.jetstream.api.kv.KeyValueEntry
import io.natskt.jetstream.api.kv.KeyValueOperation
import io.natskt.jetstream.api.kv.KeyValuePurgeOptions
import io.natskt.jetstream.api.kv.KeyValueWatchConfig
import io.natskt.jetstream.api.kv.KeyValueWatchOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
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

				assertEquals(
					bucketName,
					bucket.config
						.filterNotNull()
						.first()
						.bucket,
				)
				assertEquals(
					1u,
					bucket.config
						.filterNotNull()
						.first()
						.history,
				)
				assertEquals(
					Duration.ZERO,
					bucket.config
						.filterNotNull()
						.first()
						.ttl,
				)
				assertEquals(
					-1,
					bucket.config
						.filterNotNull()
						.first()
						.maxValueSize,
				)
				assertEquals(
					-1,
					bucket.config
						.filterNotNull()
						.first()
						.maxBytes,
				)
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

				assertEquals(createResult.config.value, getResult.config.value)
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
				bucket.close()
			}
		}

	@Test
	fun `get by revision rejects sequences belonging to a different key`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("WRONG_KEY_REV")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				val alphaRev = bucket.put("alpha", "a-value".encodeToByteArray())
				bucket.put("beta", "b-value".encodeToByteArray())

				// alphaRev is alpha's sequence; asking for it under "beta" must not surface
				// alpha's payload — the server returns by sequence regardless of subject.
				assertFailsWith<KvKeyNotFoundException> {
					bucket.get("beta", revision = alphaRev)
				}
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
	fun `history returns retained revisions oldest first`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("HISTORY")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
						history = 5
					}

				repeat(7) { i ->
					bucket.put("k", "v${i + 1}".encodeToByteArray())
				}

				val entries = bucket.history("k")
				assertEquals(5, entries.size)

				val seen = entries.map { it.value.decodeToString() }
				// Oldest of the retained 5 is v3 (v1 and v2 fell out of history=5)
				assertEquals(listOf("v3", "v4", "v5", "v6", "v7"), seen)

				val revisions = entries.map { it.revision }
				assertEquals(revisions.sorted(), revisions, "history must be ordered by revision")
			}
		}

	@Test
	fun `watchAll observes updates across keys`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("WATCHALL")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				bucket.put("a", "1".encodeToByteArray())
				bucket.put("b", "2".encodeToByteArray())

				// Construct the watcher up front so the consumer is created against the current
				// snapshot. Subsequent puts arrive as live events, not as part of the snapshot.
				val flow = bucket.watchAll()

				val updates = mutableListOf<KeyValueEntry>()
				val job =
					launch {
						withTimeout(3.seconds) {
							flow.take(4).collect { updates.add(it) }
						}
					}

				bucket.put("c", "3".encodeToByteArray())
				bucket.put("a", "1b".encodeToByteArray())

				job.join()

				val keysSeen = updates.map { it.key }.toSet()
				assertEquals(setOf("a", "b", "c"), keysSeen)
			}
		}

	@Test
	fun `multi-key watch only reports requested keys`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("WATCH_MULTI")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				// Set the watcher up before any puts so all events arrive as live updates.
				val flow = bucket.watch(listOf("a", "c"))

				val seen = mutableListOf<String>()
				val collector =
					launch {
						withTimeout(3.seconds) {
							flow.take(2).collect { seen.add(it.key) }
						}
					}

				bucket.put("a", "1".encodeToByteArray())
				bucket.put("b", "2".encodeToByteArray()) // should not be observed
				bucket.put("c", "3".encodeToByteArray())

				collector.join()
				assertEquals(setOf("a", "c"), seen.toSet())
			}
		}

	@Test
	fun `watch IgnoreDelete drops tombstones`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("WATCH_IGD")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				bucket.put("k", "v".encodeToByteArray())

				val firstSeen = CompletableDeferred<Unit>()
				val seen = mutableListOf<KeyValueEntry>()
				val job =
					launch {
						withTimeout(3.seconds) {
							bucket
								.watch(
									"k",
									KeyValueWatchConfig(options = setOf(KeyValueWatchOption.IgnoreDelete)),
								).take(2)
								.collect {
									seen.add(it)
									if (seen.size == 1) firstSeen.complete(Unit)
								}
						}
					}
				firstSeen.await() // ensure watcher has the initial snapshot before we mutate

				bucket.delete("k")
				bucket.put("k", "v2".encodeToByteArray())

				job.join()
				assertTrue(seen.none { it.operation == KeyValueOperation.Delete })
				assertEquals(2, seen.size)
				assertEquals(listOf("v", "v2"), seen.map { it.value.decodeToString() })
			}
		}

	@Test
	fun `watch UpdatesOnly skips initial snapshot`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("WATCH_UO")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				bucket.put("k", "old".encodeToByteArray())

				// Construct the watch flow before publishing — the suspend returns once the consumer is
				// created, so subsequent publishes are guaranteed to fall in the watcher's window.
				val flow =
					bucket.watch(
						"k",
						KeyValueWatchConfig(options = setOf(KeyValueWatchOption.UpdatesOnly)),
					)
				val seen = mutableListOf<KeyValueEntry>()
				val job =
					launch {
						withTimeout(3.seconds) {
							flow.take(1).collect { seen.add(it) }
						}
					}

				bucket.put("k", "new".encodeToByteArray())
				job.join()

				assertEquals(1, seen.size)
				assertEquals("new", seen.first().value.decodeToString())
			}
		}

	@Test
	fun `watch IncludeHistory replays prior revisions`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("WATCH_HIST")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
						history = 5
					}

				bucket.put("k", "1".encodeToByteArray())
				bucket.put("k", "2".encodeToByteArray())
				bucket.put("k", "3".encodeToByteArray())

				val seen = mutableListOf<KeyValueEntry>()
				val job =
					launch {
						withTimeout(3.seconds) {
							bucket
								.watch("k", KeyValueWatchConfig(options = setOf(KeyValueWatchOption.IncludeHistory)))
								.take(3)
								.collect { seen.add(it) }
						}
					}
				job.join()

				assertEquals(listOf("1", "2", "3"), seen.map { it.value.decodeToString() })
			}
		}

	@Test
	fun `watch fromRevision starts from the given sequence`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("WATCH_FROM")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
						history = 10
					}

				val seqs = (1..5).map { bucket.put("k", "v$it".encodeToByteArray()) }
				val startRev = seqs[2] // start from third revision

				val seen = mutableListOf<KeyValueEntry>()
				val job =
					launch {
						withTimeout(3.seconds) {
							bucket
								.watch("k", KeyValueWatchConfig(fromRevision = startRev))
								.take(3)
								.collect { seen.add(it) }
						}
					}
				job.join()

				assertEquals(listOf("v3", "v4", "v5"), seen.map { it.value.decodeToString() })
				assertEquals(startRev, seen.first().revision)
			}
		}

	@Test
	fun `watch MetaOnly returns empty values`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("WATCH_META")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				bucket.put("k", "loud".encodeToByteArray())

				val first = CompletableDeferred<KeyValueEntry>()
				val job =
					launch {
						withTimeout(3.seconds) {
							bucket
								.watch("k", KeyValueWatchConfig(options = setOf(KeyValueWatchOption.MetaOnly)))
								.take(1)
								.collect { first.complete(it) }
						}
					}
				job.join()

				val entry = first.await()
				assertEquals(0, entry.value.size, "MetaOnly must not deliver payload bytes")
				assertNotNull(entry.revision)
			}
		}

	@Test
	fun `consumeKeys streams without buffering and ignores tombstones`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("STREAM_KEYS")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				bucket.put("a", "1".encodeToByteArray())
				bucket.put("b", "2".encodeToByteArray())
				bucket.put("c", "3".encodeToByteArray())
				bucket.delete("b")

				val streamed = bucket.consumeKeys().toList().toSet()
				assertEquals(setOf("a", "c"), streamed)
			}
		}

	@Test
	fun `purgeDeletes removes tombstones`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("PURGE_DEL")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
					}

				bucket.put("a", "1".encodeToByteArray())
				bucket.put("b", "2".encodeToByteArray())
				bucket.put("c", "3".encodeToByteArray())
				bucket.delete("a")
				bucket.delete("b")
				bucket.purge("c")

				bucket.purgeDeletes(KeyValuePurgeOptions(noThreshold = true))

				// keys() drops tombstones so it is the cleanest way to confirm the bucket is empty.
				assertEquals(emptySet(), bucket.keys().toSet())

				// And no tombstones remain on the underlying stream — values count is zero.
				bucket.updateBucketStatus().getOrThrow()
				assertEquals(
					0uL,
					bucket.status
						.filterNotNull()
						.first()
						.values,
					"underlying stream should hold no messages",
				)
			}
		}

	@Test
	fun `per-key TTL on create expires the entry`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucketName = bucketName("TTL_CREATE")
				val bucket =
					js.keyValueManager.create {
						name = bucketName
						limitMarkerTtl = 1.seconds
					}

				bucket.create("ephemeral", "burns".encodeToByteArray(), ttl = 1.seconds)
				assertEquals("burns", bucket.get("ephemeral").value.decodeToString())

				val deadline = Clock.System.now() + 5.seconds
				var stillThere = true
				while (Clock.System.now() < deadline) {
					if ("ephemeral" !in bucket.keys()) {
						stillThere = false
						break
					}
				}
				assertTrue(!stillThere, "ephemeral should age out after the per-key TTL elapses")
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

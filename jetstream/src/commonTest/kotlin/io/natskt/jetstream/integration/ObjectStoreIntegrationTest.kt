package io.natskt.jetstream.integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.os.GetLinkToBucket
import io.natskt.jetstream.api.os.LinkNotAllowedOnPut
import io.natskt.jetstream.api.os.ObjectAlreadyExists
import io.natskt.jetstream.api.os.ObjectInfo
import io.natskt.jetstream.api.os.ObjectMeta
import io.natskt.jetstream.api.os.ObjectMetaOptions
import io.natskt.jetstream.api.os.ObjectNotFound
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ObjectStoreIntegrationTest {
	private var counter = 1

	private fun bucketName(base: String): String = "${base}_${counter++}"

	@Test
	fun `it creates a bucket`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSBasic")
				val store =
					js.objectStoreManager.create {
						name = bucket
						description = "small docs"
					}
				assertEquals(bucket, store.config!!.bucket)
				assertEquals("small docs", store.config!!.description)
				store.close()
			}
		}

	@Test
	fun `it gets an existing bucket`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSGet")
				val created =
					js.objectStoreManager.create {
						name = bucket
					}
				val fetched = js.objectStoreManager.get(bucket)
				assertEquals(created.config, fetched.config)
				created.close()
				fetched.close()
			}
		}

	@Test
	fun `it puts and gets a small object`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSPut")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}
				val payload = "hello object store".encodeToByteArray()
				val info = store.put("greeting.txt", payload)
				assertEquals("greeting.txt", info.name)
				assertEquals(payload.size.toLong(), info.size)
				assertEquals(1L, info.chunks)
				assertNotNull(info.digest)
				assertTrue(info.digest!!.startsWith("SHA-256="))

				val result = store.get("greeting.txt")
				assertContentEquals(payload, result.data)
				assertEquals(info.nuid, result.info.nuid)

				store.close()
			}
		}

	@Test
	fun `getInfo returns null for missing object`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSMissing")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}
				assertNull(store.getInfo("does-not-exist"))
				assertFailsWith<ObjectNotFound> { store.get("does-not-exist") }
				store.close()
			}
		}

	@Test
	fun `it streams a multi-chunk object`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSStream")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}

				val payload = Random(42).nextBytes(300_000)
				val info = store.put(ObjectMeta.objectName("blob.bin"), payload)
				assertEquals(payload.size.toLong(), info.size)
				assertTrue(info.chunks > 1)

				val streamResult = store.getStream("blob.bin")
				assertEquals(info.size, streamResult.info.size)
				val chunks = streamResult.data.toList()
				val recombined = ByteArray(payload.size)
				var offset = 0
				for (chunk in chunks) {
					chunk.copyInto(recombined, offset)
					offset += chunk.size
				}
				assertContentEquals(payload, recombined)

				store.close()
			}
		}

	@Test
	fun `it puts from a Flow`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSFlow")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}

				val pieces =
					listOf(
						"hello, ".encodeToByteArray(),
						"object ".encodeToByteArray(),
						"store!".encodeToByteArray(),
					)
				val info = store.put(ObjectMeta.objectName("flow.txt"), flowOf(*pieces.toTypedArray()))

				val expected = pieces.fold(ByteArray(0)) { acc, b -> acc + b }
				assertEquals(expected.size.toLong(), info.size)
				assertContentEquals(expected, store.get("flow.txt").data)

				store.close()
			}
		}

	@Test
	fun `it lists objects skipping deletes`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSList")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}

				store.put("a", "alpha".encodeToByteArray())
				store.put("b", "bravo".encodeToByteArray())
				store.put("c", "charlie".encodeToByteArray())
				store.delete("b")

				val names = store.getList().map { it.name }.toSet()
				assertEquals(setOf("a", "c"), names)

				val tombstone = store.getInfo("b", includingDeleted = true)
				assertNotNull(tombstone)
				assertTrue(tombstone!!.deleted)

				store.close()
			}
		}

	@Test
	fun `it watches with snapshot done sentinel`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSWatch")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}

				store.put("seed", "42".encodeToByteArray())

				val firstNull = CompletableDeferred<Unit>()
				val updates = mutableListOf<ObjectInfo?>()
				val job =
					launch {
						withTimeout(5.seconds) {
							store
								.watch()
								.take(3)
								.collect { info ->
									updates.add(info)
									if (info == null && !firstNull.isCompleted) firstNull.complete(Unit)
								}
						}
					}

				firstNull.await()
				store.put("after", "99".encodeToByteArray())
				job.join()

				assertEquals(3, updates.size)
				val nonNull = updates.filterNotNull()
				assertTrue(nonNull.any { it.name == "seed" })
				assertTrue(nonNull.any { it.name == "after" })

				store.close()
			}
		}

	@Test
	fun `it stores and retrieves an empty object`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSEmpty")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}
				val info = store.put("zero", ByteArray(0))
				assertEquals(0L, info.size)
				assertEquals(0L, info.chunks)
				val result = store.get("zero")
				assertContentEquals(ByteArray(0), result.data)
				store.close()
			}
		}

	@Test
	fun `it overwrites with a smaller payload purging old chunks`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSOverwrite")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}
				val first = store.put("doc", Random(7).nextBytes(200_000))
				val second = store.put("doc", "tiny".encodeToByteArray())
				assertTrue(first.nuid != second.nuid)
				assertContentEquals("tiny".encodeToByteArray(), store.get("doc").data)
				store.close()
			}
		}

	@Test
	fun `updateMeta renames preserving chunks`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSMeta")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}

				store.put("first", "payload".encodeToByteArray())
				val original = store.getInfo("first")!!
				val renamed = store.updateMeta("first", ObjectMeta(name = "second", description = "renamed"))
				assertEquals("second", renamed.name)
				assertEquals("renamed", renamed.description)
				assertEquals(original.nuid, renamed.nuid)
				assertNull(store.getInfo("first"))
				assertContentEquals("payload".encodeToByteArray(), store.get("second").data)

				store.close()
			}
		}

	@Test
	fun `addLink follows to target object`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSLink")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}

				val source = store.put("real", "underlying".encodeToByteArray())
				store.addLink("alias", source)
				val read = store.get("alias")
				assertContentEquals("underlying".encodeToByteArray(), read.data)

				store.close()
			}
		}

	@Test
	fun `addBucketLink rejects content reads`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val a = bucketName("OSLinkA")
				val b = bucketName("OSLinkB")
				val storeA =
					js.objectStoreManager.create {
						name = a
					}
				val storeB =
					js.objectStoreManager.create {
						name = b
					}

				storeA.addBucketLink("toB", storeB)
				assertFailsWith<GetLinkToBucket> { storeA.get("toB") }

				storeA.close()
				storeB.close()
			}
		}

	@Test
	fun `seal blocks subsequent puts`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSSeal")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}

				store.put("first", "a".encodeToByteArray())
				val status = store.seal()
				assertTrue(status.isSealed)

				assertFailsWith<JetStreamApiException> {
					store.put("after-seal", "b".encodeToByteArray())
				}

				store.close()
			}
		}

	@Test
	fun `put rejects link in meta`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSPutLink")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}

				val meta =
					ObjectMeta(
						name = "x",
						options =
							ObjectMetaOptions(
								link =
									io.natskt.jetstream.api.os.ObjectLink
										.bucket(bucket),
							),
					)
				assertFailsWith<LinkNotAllowedOnPut> {
					store.put(meta, "data".encodeToByteArray())
				}

				store.close()
			}
		}

	@Test
	fun `addLink rejects existing object`() =
		RemoteNatsHarness.runBlocking { server ->
			withJetStreamClient(server) { _, js ->
				val bucket = bucketName("OSLinkExisting")
				val store =
					js.objectStoreManager.create {
						name = bucket
					}

				val target = store.put("target", "value".encodeToByteArray())
				store.put("collision", "other".encodeToByteArray())
				assertFailsWith<ObjectAlreadyExists> {
					store.addLink("collision", target)
				}

				store.close()
			}
		}
}

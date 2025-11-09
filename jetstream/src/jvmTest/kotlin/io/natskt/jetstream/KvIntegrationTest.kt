package io.natskt.jetstream

import harness.NatsServerHarness
import io.natskt.NatsClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

class KvIntegrationTest {
	@Test
	fun `it creates a bucket`() =
		NatsServerHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

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

	@Test
	fun `it gets an existing bucket`() =
		NatsServerHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

			val createResult =
				js.keyValueManager.create {
					name = "MyCoolBucketAgain"
				}

			val getResult = js.keyValueManager.get("MyCoolBucketAgain")

			assertEquals(createResult.config, getResult.config)
		}

	@Test
	fun `it puts on a bucket`() =
		NatsServerHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

			js.keyValueManager.create {
				name = "MyCoolBucket"
			}

			val bucket = js.keyValue("MyCoolBucket")

			bucket.put("a.b", "test".encodeToByteArray())
		}

	@Test
	fun `it gets on a bucket`() =
		NatsServerHarness.runBlocking { server ->
			val c = NatsClient(server.uri).also { it.connect() }
			val js = JetStreamClient(c)

			js.keyValueManager.create {
				name = "MyCoolBucket"
			}

			val bucket = js.keyValue("MyCoolBucket")

			bucket.put("a.b", "test1".encodeToByteArray())

			val entry = bucket.get("a.b")

			assertEquals("test1", entry.value.decodeToString())
		}
}

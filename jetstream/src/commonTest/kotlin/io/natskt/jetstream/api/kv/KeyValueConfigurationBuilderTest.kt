package io.natskt.jetstream.api.kv

import io.natskt.jetstream.api.StorageType
import io.natskt.jetstream.api.StreamCompression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class KeyValueConfigurationBuilderTest {
	private fun build(configure: KeyValueConfigurationBuilder.() -> Unit): KeyValueConfig =
		KeyValueConfigurationBuilder().apply(configure).build()

	// ──────────────────────────────────────────────────────────
	// Validation: name
	// ──────────────────────────────────────────────────────────

	@Test
	fun `blank name throws IllegalStateException`() {
		assertFailsWith<IllegalStateException> {
			build { name = "" }
		}
	}

	@Test
	fun `whitespace-only name throws`() {
		assertFailsWith<Exception> {
			build { name = "   " }
		}
	}

	@Test
	fun `invalid name with dot throws`() {
		assertFailsWith<Exception> {
			build { name = "bucket.name" }
		}
	}

	@Test
	fun `invalid name with wildcard throws`() {
		assertFailsWith<Exception> {
			build { name = "my*bucket" }
		}
	}

	@Test
	fun `valid name is accepted`() {
		val config = build { name = "my-bucket" }
		assertEquals("my-bucket", config.bucket)
	}

	// ──────────────────────────────────────────────────────────
	// Validation: history boundaries
	// ──────────────────────────────────────────────────────────

	@Test
	fun `history of 0 throws`() {
		val ex = assertFailsWith<IllegalArgumentException> {
			build {
				name = "bucket"
				history = 0
			}
		}
		assertTrue(ex.message!!.contains("history"), "error should mention history: ${ex.message}")
	}

	@Test
	fun `history of 1 is accepted (minimum)`() {
		val config = build {
			name = "bucket"
			history = 1
		}
		assertEquals(1.toUShort(), config.history)
	}

	@Test
	fun `history of 64 is accepted (maximum)`() {
		val config = build {
			name = "bucket"
			history = 64
		}
		assertEquals(64.toUShort(), config.history)
	}

	@Test
	fun `history of 65 throws (exceeds maximum)`() {
		assertFailsWith<IllegalArgumentException> {
			build {
				name = "bucket"
				history = 65
			}
		}
	}

	@Test
	fun `default history is 1`() {
		val config = build { name = "bucket" }
		assertEquals(1.toUShort(), config.history)
	}

	// ──────────────────────────────────────────────────────────
	// New fields in this PR
	// ──────────────────────────────────────────────────────────

	@Test
	fun `description field is stored`() {
		val config = build {
			name = "bucket"
			description = "my kv store"
		}
		assertEquals("my kv store", config.description)
	}

	@Test
	fun `description defaults to null`() {
		val config = build { name = "bucket" }
		assertNull(config.description)
	}

	@Test
	fun `maxValueSize field is stored`() {
		val config = build {
			name = "bucket"
			maxValueSize = 1024
		}
		assertEquals(1024, config.maxValueSize)
	}

	@Test
	fun `maxValueSize defaults to null`() {
		val config = build { name = "bucket" }
		assertNull(config.maxValueSize)
	}

	@Test
	fun `maxBytes field is stored`() {
		val config = build {
			name = "bucket"
			maxBytes = 10_000_000L
		}
		assertEquals(10_000_000L, config.maxBytes)
	}

	@Test
	fun `ttl field is stored`() {
		val config = build {
			name = "bucket"
			ttl = 7.days
		}
		assertEquals(7.days, config.ttl)
	}

	@Test
	fun `ttl defaults to null`() {
		val config = build { name = "bucket" }
		assertNull(config.ttl)
	}

	@Test
	fun `storage field is stored`() {
		val config = build {
			name = "bucket"
			storage = StorageType.Memory
		}
		assertEquals(StorageType.Memory, config.storage)
	}

	@Test
	fun `storage defaults to null`() {
		val config = build { name = "bucket" }
		assertNull(config.storage)
	}

	@Test
	fun `replicas field is stored`() {
		val config = build {
			name = "bucket"
			replicas = 3
		}
		assertEquals(3, config.replicas)
	}

	@Test
	fun `replicas defaults to null`() {
		val config = build { name = "bucket" }
		assertNull(config.replicas)
	}

	@Test
	fun `compression field is stored`() {
		val config = build {
			name = "bucket"
			compression = StreamCompression.S2
		}
		assertEquals(StreamCompression.S2, config.compression)
	}

	@Test
	fun `limitMarkerTtl field is stored`() {
		val config = build {
			name = "bucket"
			limitMarkerTtl = 30.seconds
		}
		assertEquals(30.seconds, config.limitMarkerTtl)
	}

	@Test
	fun `limitMarkerTtl defaults to null`() {
		val config = build { name = "bucket" }
		assertNull(config.limitMarkerTtl)
	}

	@Test
	fun `metadata helper adds key-value pairs`() {
		val config = build {
			name = "bucket"
			metadata("env", "production")
			metadata("owner", "team-a")
		}
		assertNotNull(config.metadata)
		assertEquals("production", config.metadata!!["env"])
		assertEquals("team-a", config.metadata!!["owner"])
	}

	@Test
	fun `metadata defaults to null`() {
		val config = build { name = "bucket" }
		assertNull(config.metadata)
	}

	@Test
	fun `built metadata map is immutable copy`() {
		val builder = KeyValueConfigurationBuilder().apply {
			name = "bucket"
			metadata("key", "value")
		}
		val config = builder.build()
		// Mutating builder after build should not affect config
		builder.metadata!!["key"] = "changed"
		assertEquals("value", config.metadata!!["key"])
	}

	@Test
	fun `bucket name is set correctly`() {
		val config = build { name = "test-bucket" }
		assertEquals("test-bucket", config.bucket)
	}
}
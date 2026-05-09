package io.natskt.jetstream.api.consumer

import io.natskt.jetstream.api.AckPolicy
import io.natskt.jetstream.api.PriorityPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ConsumerConfigurationBuilderTest {
	private fun build(configure: ConsumerConfigurationBuilder.() -> Unit) =
		ConsumerConfigurationBuilder().apply(configure).build()

	// ──────────────────────────────────────────────────────────
	// name field (new in this PR)
	// ──────────────────────────────────────────────────────────

	@Test
	fun `name field is stored in ConsumerConfig`() {
		val config = build { name = "my-consumer" }
		assertEquals("my-consumer", config.name)
	}

	@Test
	fun `name is null by default`() {
		val config = build { durableName = "durable" }
		assertNull(config.name)
	}

	@Test
	fun `durableName and matching name are both accepted`() {
		val config = build {
			durableName = "same"
			name = "same"
		}
		assertEquals("same", config.durableName)
		assertEquals("same", config.name)
	}

	@Test
	fun `conflicting durableName and name throws IllegalArgumentException`() {
		val ex = assertFailsWith<IllegalArgumentException> {
			build {
				durableName = "durable"
				name = "different"
			}
		}
		assertTrue(ex.message!!.contains("durableName") || ex.message!!.contains("name"),
			"error message should mention durableName or name: ${ex.message}")
	}

	@Test
	fun `invalid name token throws`() {
		assertFailsWith<Exception> {
			build { name = "invalid.name" }
		}
	}

	@Test
	fun `invalid durableName token throws`() {
		assertFailsWith<Exception> {
			build { durableName = "invalid name" }
		}
	}

	// ──────────────────────────────────────────────────────────
	// Priority fields (new in this PR)
	// ──────────────────────────────────────────────────────────

	@Test
	fun `priorityPolicy is null by default`() {
		val config = build { durableName = "consumer" }
		assertNull(config.priorityPolicy)
	}

	@Test
	fun `priorityPolicy None is accepted without priority groups`() {
		val config = build {
			durableName = "consumer"
			priorityPolicy = PriorityPolicy.None
		}
		assertEquals(PriorityPolicy.None, config.priorityPolicy)
	}

	@Test
	fun `priorityPolicy Overflow requires at least one priority group`() {
		val ex = assertFailsWith<IllegalArgumentException> {
			build {
				durableName = "consumer"
				priorityPolicy = PriorityPolicy.Overflow
			}
		}
		assertTrue(ex.message!!.contains("priorityPolicy") || ex.message!!.contains("priority group"),
			"error message should mention policy or group: ${ex.message}")
	}

	@Test
	fun `priorityPolicy PinnedClient requires at least one priority group`() {
		val ex = assertFailsWith<IllegalArgumentException> {
			build {
				durableName = "consumer"
				priorityPolicy = PriorityPolicy.PinnedClient
			}
		}
		assertTrue(ex.message!!.contains("priorityPolicy") || ex.message!!.contains("priority group"),
			"error message should mention policy or group: ${ex.message}")
	}

	@Test
	fun `priorityPolicy Overflow with groups succeeds`() {
		val config = build {
			durableName = "consumer"
			priorityPolicy = PriorityPolicy.Overflow
			priorityGroup("group-a")
		}
		assertEquals(PriorityPolicy.Overflow, config.priorityPolicy)
		assertEquals(listOf("group-a"), config.priorityGroups)
	}

	@Test
	fun `priorityGroup helper adds groups to the list`() {
		val config = build {
			durableName = "consumer"
			priorityPolicy = PriorityPolicy.PinnedClient
			priorityGroup("g1")
			priorityGroup("g2")
		}
		assertEquals(listOf("g1", "g2"), config.priorityGroups)
	}

	@Test
	fun `priorityGroups is null by default`() {
		val config = build { durableName = "consumer" }
		assertNull(config.priorityGroups)
	}

	@Test
	fun `priorityTimeout field is stored`() {
		val config = build {
			durableName = "consumer"
			priorityTimeout = 10.seconds
		}
		assertEquals(10.seconds, config.priorityTimeout)
	}

	@Test
	fun `priorityTimeout is null by default`() {
		val config = build { durableName = "consumer" }
		assertNull(config.priorityTimeout)
	}

	// ──────────────────────────────────────────────────────────
	// pauseUntil field (new in this PR)
	// ──────────────────────────────────────────────────────────

	@Test
	fun `pauseUntil is null by default`() {
		val config = build { durableName = "consumer" }
		assertNull(config.pauseUntil)
	}

	@Test
	fun `pauseUntil is stored in ConsumerConfig`() {
		val instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)
		val config = build {
			durableName = "consumer"
			pauseUntil = instant
		}
		assertEquals(instant, config.pauseUntil)
	}

	// ──────────────────────────────────────────────────────────
	// Pre-existing fields that were extended (still working)
	// ──────────────────────────────────────────────────────────

	@Test
	fun `ackPolicy field still works`() {
		val config = build {
			durableName = "consumer"
			ackPolicy = AckPolicy.None
		}
		assertEquals(AckPolicy.None, config.ackPolicy)
	}

	@Test
	fun `built priorityGroups list is immutable copy`() {
		val builder = ConsumerConfigurationBuilder().apply {
			durableName = "consumer"
			priorityPolicy = PriorityPolicy.Overflow
			priorityGroup("g1")
		}
		val config = builder.build()
		// Mutating builder after build should not affect the built config
		builder.priorityGroups!!.add("g2")
		assertEquals(listOf("g1"), config.priorityGroups)
	}
}
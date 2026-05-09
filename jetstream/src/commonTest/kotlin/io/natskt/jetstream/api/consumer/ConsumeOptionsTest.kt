package io.natskt.jetstream.api.consumer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class ConsumeOptionsTest {
	@Test
	fun `default batch is 500`() {
		assertEquals(500, ConsumeOptions().batch)
	}

	@Test
	fun `default expires is 30 seconds`() {
		assertEquals(30.seconds, ConsumeOptions().expires)
	}

	@Test
	fun `default maxBytes is null`() {
		assertNull(ConsumeOptions().maxBytes)
	}

	@Test
	fun `default thresholdMessages is null (means use batch divided by 2)`() {
		assertNull(ConsumeOptions().thresholdMessages)
	}

	@Test
	fun `default idleHeartbeat is null`() {
		assertNull(ConsumeOptions().idleHeartbeat)
	}

	@Test
	fun `default group is null`() {
		assertNull(ConsumeOptions().group)
	}

	@Test
	fun `default minPending is null`() {
		assertNull(ConsumeOptions().minPending)
	}

	@Test
	fun `default minAckPending is null`() {
		assertNull(ConsumeOptions().minAckPending)
	}

	@Test
	fun `custom batch is stored`() {
		val opts = ConsumeOptions(batch = 100)
		assertEquals(100, opts.batch)
	}

	@Test
	fun `custom expires is stored`() {
		val opts = ConsumeOptions(expires = 10.seconds)
		assertEquals(10.seconds, opts.expires)
	}

	@Test
	fun `custom maxBytes is stored`() {
		val opts = ConsumeOptions(maxBytes = 1_000_000)
		assertEquals(1_000_000, opts.maxBytes)
	}

	@Test
	fun `custom thresholdMessages is stored`() {
		val opts = ConsumeOptions(batch = 100, thresholdMessages = 25)
		assertEquals(25, opts.thresholdMessages)
	}

	@Test
	fun `custom idleHeartbeat is stored`() {
		val opts = ConsumeOptions(idleHeartbeat = 5.seconds)
		assertEquals(5.seconds, opts.idleHeartbeat)
	}

	@Test
	fun `custom group is stored`() {
		val opts = ConsumeOptions(group = "priority-group-1")
		assertEquals("priority-group-1", opts.group)
	}

	@Test
	fun `custom minPending is stored`() {
		val opts = ConsumeOptions(minPending = 10L)
		assertEquals(10L, opts.minPending)
	}

	@Test
	fun `custom minAckPending is stored`() {
		val opts = ConsumeOptions(minAckPending = 5L)
		assertEquals(5L, opts.minAckPending)
	}

	@Test
	fun `ConsumeOptions is a data class with value equality`() {
		val a = ConsumeOptions(batch = 200, expires = 15.seconds)
		val b = ConsumeOptions(batch = 200, expires = 15.seconds)
		assertEquals(a, b)
	}

	@Test
	fun `copy produces independent instance`() {
		val original = ConsumeOptions(batch = 100, group = "g1")
		val copy = original.copy(batch = 200)
		assertEquals(100, original.batch)
		assertEquals(200, copy.batch)
		assertEquals("g1", copy.group)
	}

	@Test
	fun `all priority fields can be set together`() {
		val opts = ConsumeOptions(
			batch = 50,
			group = "priority",
			minPending = 100L,
			minAckPending = 10L,
		)
		assertEquals(50, opts.batch)
		assertEquals("priority", opts.group)
		assertEquals(100L, opts.minPending)
		assertEquals(10L, opts.minAckPending)
	}
}
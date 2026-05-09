package io.natskt.jetstream.api

import io.natskt.internal.wireJsonFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsumerPullRequestSerializationTest {
	@Test
	fun `default request omits all optional fields`() {
		val json = wireJsonFormat.encodeToString(ConsumerPullRequest())
		assertEquals("{}", json)
	}

	@Test
	fun `priority fields encode with nats-go canonical snake_case keys`() {
		val req =
			ConsumerPullRequest(
				batch = 10,
				group = "g1",
				minPending = 100,
				minAckPending = 5,
			)
		val json = wireJsonFormat.encodeToString(req)

		assertTrue(""""batch":10""" in json, "missing batch: $json")
		assertTrue(""""group":"g1"""" in json, "missing group: $json")
		assertTrue(""""min_pending":100""" in json, "missing min_pending: $json")
		assertTrue(""""min_ack_pending":5""" in json, "missing min_ack_pending: $json")
	}

	@Test
	fun `unset priority fields are omitted from the wire payload`() {
		val req = ConsumerPullRequest(batch = 1, expires = 1_000_000_000L)
		val json = wireJsonFormat.encodeToString(req)

		assertFalse("group" in json, "group key should be absent: $json")
		assertFalse("min_pending" in json, "min_pending key should be absent: $json")
		assertFalse("min_ack_pending" in json, "min_ack_pending key should be absent: $json")
	}

	@Test
	fun `request round-trips through json`() {
		val original =
			ConsumerPullRequest(
				expires = 30_000_000_000L,
				batch = 50,
				noWait = false,
				maxBytes = 1024,
				idleHeartbeat = 5_000_000_000L,
				group = "priority_group_a",
				minPending = 25,
				minAckPending = 3,
			)
		val json = wireJsonFormat.encodeToString(original)
		val restored = wireJsonFormat.decodeFromString<ConsumerPullRequest>(json)
		assertEquals(original, restored)
	}
}

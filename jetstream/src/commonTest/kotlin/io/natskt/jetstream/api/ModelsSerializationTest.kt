package io.natskt.jetstream.api

import io.natskt.internal.wireJsonFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelsSerializationTest {
	// ──────────────────────────────────────────────────────────
	// PersistMode serialization (new in this PR)
	// ──────────────────────────────────────────────────────────

	@Test
	fun `PersistMode Async serializes to async`() {
		val json = wireJsonFormat.encodeToString(PersistMode.serializer(), PersistMode.Async)
		assertEquals(""""async"""", json)
	}

	@Test
	fun `PersistMode Sync serializes to sync`() {
		val json = wireJsonFormat.encodeToString(PersistMode.serializer(), PersistMode.Sync)
		assertEquals(""""sync"""", json)
	}

	@Test
	fun `PersistMode round-trips through JSON`() {
		listOf(PersistMode.Async, PersistMode.Sync).forEach { mode ->
			val json = wireJsonFormat.encodeToString(PersistMode.serializer(), mode)
			val decoded = wireJsonFormat.decodeFromString(PersistMode.serializer(), json)
			assertEquals(mode, decoded, "round-trip failed for $mode")
		}
	}

	// ──────────────────────────────────────────────────────────
	// PriorityPolicy serialization (new in this PR)
	// ──────────────────────────────────────────────────────────

	@Test
	fun `PriorityPolicy None serializes to none`() {
		val json = wireJsonFormat.encodeToString(PriorityPolicy.serializer(), PriorityPolicy.None)
		assertEquals(""""none"""", json)
	}

	@Test
	fun `PriorityPolicy Overflow serializes to overflow`() {
		val json = wireJsonFormat.encodeToString(PriorityPolicy.serializer(), PriorityPolicy.Overflow)
		assertEquals(""""overflow"""", json)
	}

	@Test
	fun `PriorityPolicy PinnedClient serializes to pinned_client`() {
		val json = wireJsonFormat.encodeToString(PriorityPolicy.serializer(), PriorityPolicy.PinnedClient)
		assertEquals(""""pinned_client"""", json)
	}

	@Test
	fun `PriorityPolicy round-trips through JSON`() {
		listOf(PriorityPolicy.None, PriorityPolicy.Overflow, PriorityPolicy.PinnedClient).forEach { policy ->
			val json = wireJsonFormat.encodeToString(PriorityPolicy.serializer(), policy)
			val decoded = wireJsonFormat.decodeFromString(PriorityPolicy.serializer(), json)
			assertEquals(policy, decoded, "round-trip failed for $policy")
		}
	}

	// ──────────────────────────────────────────────────────────
	// StreamConsumerLimits serialization (new in this PR)
	// ──────────────────────────────────────────────────────────

	@Test
	fun `StreamConsumerLimits with null fields serializes to empty object`() {
		val limits = StreamConsumerLimits()
		val json = wireJsonFormat.encodeToString(StreamConsumerLimits.serializer(), limits)
		assertEquals("{}", json)
	}

	@Test
	fun `StreamConsumerLimits maxAckPending serializes with snake_case key`() {
		val limits = StreamConsumerLimits(maxAckPending = 100)
		val json = wireJsonFormat.encodeToString(StreamConsumerLimits.serializer(), limits)
		assertTrue(""""max_ack_pending":100""" in json, "missing max_ack_pending: $json")
	}

	@Test
	fun `StreamConsumerLimits round-trips through JSON`() {
		val original = StreamConsumerLimits(maxAckPending = 50)
		val json = wireJsonFormat.encodeToString(StreamConsumerLimits.serializer(), original)
		val restored = wireJsonFormat.decodeFromString(StreamConsumerLimits.serializer(), json)
		assertEquals(original, restored)
	}

	// ──────────────────────────────────────────────────────────
	// ConsumerConfig priority fields (new in this PR)
	// ──────────────────────────────────────────────────────────

	@Test
	fun `ConsumerConfig priority_policy encodes with snake_case key`() {
		val config = ConsumerConfig(priorityPolicy = PriorityPolicy.PinnedClient)
		val json = wireJsonFormat.encodeToString(ConsumerConfig.serializer(), config)
		assertTrue(""""priority_policy":"pinned_client"""" in json, "missing priority_policy: $json")
	}

	@Test
	fun `ConsumerConfig priority_groups encodes as array`() {
		val config = ConsumerConfig(priorityGroups = listOf("g1", "g2"))
		val json = wireJsonFormat.encodeToString(ConsumerConfig.serializer(), config)
		assertTrue(""""priority_groups":["g1","g2"]""" in json, "missing priority_groups: $json")
	}

	@Test
	fun `ConsumerConfig name field encodes without serial-name alias`() {
		val config = ConsumerConfig(name = "my-consumer")
		val json = wireJsonFormat.encodeToString(ConsumerConfig.serializer(), config)
		assertTrue(""""name":"my-consumer"""" in json, "missing name: $json")
	}

	@Test
	fun `ConsumerConfig unset priority fields are omitted`() {
		val config = ConsumerConfig(name = "basic")
		val json = wireJsonFormat.encodeToString(ConsumerConfig.serializer(), config)
		assertFalse("priority_policy" in json, "priority_policy should be absent: $json")
		assertFalse("priority_groups" in json, "priority_groups should be absent: $json")
		assertFalse("priority_timeout" in json, "priority_timeout should be absent: $json")
		assertFalse("pause_until" in json, "pause_until should be absent: $json")
	}

	@Test
	fun `ConsumerConfig priority fields round-trip through JSON`() {
		val original =
			ConsumerConfig(
				name = "priority-consumer",
				priorityPolicy = PriorityPolicy.Overflow,
				priorityGroups = listOf("group-a", "group-b"),
			)
		val json = wireJsonFormat.encodeToString(ConsumerConfig.serializer(), original)
		val restored = wireJsonFormat.decodeFromString(ConsumerConfig.serializer(), json)
		assertEquals(original.priorityPolicy, restored.priorityPolicy)
		assertEquals(original.priorityGroups, restored.priorityGroups)
		assertEquals(original.name, restored.name)
	}

	// ──────────────────────────────────────────────────────────
	// StreamConfig new fields (new in this PR)
	// ──────────────────────────────────────────────────────────

	@Test
	fun `StreamConfig persist_mode encodes with snake_case key`() {
		val config = StreamConfig(name = "mystream", persistMode = PersistMode.Async)
		val json = wireJsonFormat.encodeToString(StreamConfig.serializer(), config)
		assertTrue(""""persist_mode":"async"""" in json, "missing persist_mode: $json")
	}

	@Test
	fun `StreamConfig first_seq encodes as first_seq`() {
		val config = StreamConfig(name = "mystream", firstSequence = 42u)
		val json = wireJsonFormat.encodeToString(StreamConfig.serializer(), config)
		assertTrue(""""first_seq":42""" in json, "missing first_seq: $json")
	}

	@Test
	fun `StreamConfig allow_atomic encodes as allow_atomic`() {
		val config = StreamConfig(name = "mystream", allowAtomicPublish = true)
		val json = wireJsonFormat.encodeToString(StreamConfig.serializer(), config)
		assertTrue(""""allow_atomic":true""" in json, "missing allow_atomic: $json")
	}

	@Test
	fun `StreamConfig allow_batched encodes as allow_batched`() {
		val config = StreamConfig(name = "mystream", allowBatched = true)
		val json = wireJsonFormat.encodeToString(StreamConfig.serializer(), config)
		assertTrue(""""allow_batched":true""" in json, "missing allow_batched: $json")
	}

	@Test
	fun `StreamConfig consumer_limits encodes correctly`() {
		val config = StreamConfig(
			name = "mystream",
			consumerLimits = StreamConsumerLimits(maxAckPending = 100),
		)
		val json = wireJsonFormat.encodeToString(StreamConfig.serializer(), config)
		assertTrue(""""consumer_limits":{""" in json, "missing consumer_limits: $json")
		assertTrue(""""max_ack_pending":100""" in json, "missing max_ack_pending: $json")
	}

	// ──────────────────────────────────────────────────────────
	// ConsumerPullRequest new fields
	// ──────────────────────────────────────────────────────────

	@Test
	fun `ConsumerPullRequest new group field serializes to group key`() {
		val req = ConsumerPullRequest(batch = 10, group = "priority-group")
		val json = wireJsonFormat.encodeToString(ConsumerPullRequest.serializer(), req)
		assertTrue(""""group":"priority-group"""" in json, "missing group: $json")
	}

	@Test
	fun `ConsumerPullRequest min_pending serializes with snake_case key`() {
		val req = ConsumerPullRequest(batch = 10, minPending = 50L)
		val json = wireJsonFormat.encodeToString(ConsumerPullRequest.serializer(), req)
		assertTrue(""""min_pending":50""" in json, "missing min_pending: $json")
	}

	@Test
	fun `ConsumerPullRequest min_ack_pending serializes with snake_case key`() {
		val req = ConsumerPullRequest(batch = 10, minAckPending = 5L)
		val json = wireJsonFormat.encodeToString(ConsumerPullRequest.serializer(), req)
		assertTrue(""""min_ack_pending":5""" in json, "missing min_ack_pending: $json")
	}
}
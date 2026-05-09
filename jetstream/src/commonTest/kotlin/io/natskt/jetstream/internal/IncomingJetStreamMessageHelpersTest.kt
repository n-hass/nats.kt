package io.natskt.jetstream.internal

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class IncomingJetStreamMessageHelpersTest {
	// ──────────────────────────────────────────────────────────
	// nakWithDelayBody (new in this PR)
	// ──────────────────────────────────────────────────────────

	@Test
	fun `nakWithDelayBody produces correct format`() {
		val body = nakWithDelayBody(1.seconds)
		val text = body.decodeToString()
		assertTrue(text.startsWith("-NAK "), "should start with -NAK: $text")
		assertTrue("""{"delay":""" in text, "should contain delay key: $text")
		assertTrue(text.endsWith("}"), "should end with }: $text")
	}

	@Test
	fun `nakWithDelayBody includes correct nanosecond value for 1 second`() {
		val body = nakWithDelayBody(1.seconds)
		val text = body.decodeToString()
		// 1 second = 1_000_000_000 nanoseconds
		assertTrue(""""delay":1000000000""" in text, "1s should be 1_000_000_000ns: $text")
	}

	@Test
	fun `nakWithDelayBody includes correct nanosecond value for 500ms`() {
		val body = nakWithDelayBody(500.milliseconds)
		val text = body.decodeToString()
		// 500ms = 500_000_000 nanoseconds
		assertTrue(""""delay":500000000""" in text, "500ms should be 500_000_000ns: $text")
	}

	@Test
	fun `nakWithDelayBody handles zero duration`() {
		val body = nakWithDelayBody(Duration.ZERO)
		val text = body.decodeToString()
		assertTrue(""""delay":0""" in text, "zero duration should produce delay:0: $text")
	}

	@Test
	fun `nakWithDelayBody returns ByteArray`() {
		val body = nakWithDelayBody(100.milliseconds)
		assertTrue(body.isNotEmpty(), "should return non-empty byte array")
		assertContentEquals(
			("-NAK {\"delay\":100000000}").encodeToByteArray(),
			body,
		)
	}

	@Test
	fun `nakWithDelayBody handles sub-millisecond precision`() {
		val body = nakWithDelayBody(1.nanoseconds)
		val text = body.decodeToString()
		assertTrue(""""delay":1""" in text, "1 nanosecond should produce delay:1: $text")
	}

	// ──────────────────────────────────────────────────────────
	// termWithReasonBody (new in this PR)
	// ──────────────────────────────────────────────────────────

	@Test
	fun `termWithReasonBody produces correct format`() {
		val body = termWithReasonBody("bad message")
		val text = body.decodeToString()
		assertTrue(text.startsWith("+TERM "), "should start with +TERM: $text")
		assertTrue("bad message" in text, "should contain the reason: $text")
	}

	@Test
	fun `termWithReasonBody contains the exact reason`() {
		val reason = "Message violates consumer filter"
		val body = termWithReasonBody(reason)
		assertContentEquals(
			("+TERM $reason").encodeToByteArray(),
			body,
		)
	}

	@Test
	fun `termWithReasonBody handles single-word reason`() {
		val body = termWithReasonBody("invalid")
		assertContentEquals(
			"+TERM invalid".encodeToByteArray(),
			body,
		)
	}

	@Test
	fun `termWithReasonBody returns ByteArray`() {
		val body = termWithReasonBody("reason")
		assertTrue(body.isNotEmpty(), "should return non-empty byte array")
	}

	// ──────────────────────────────────────────────────────────
	// ACK constant verification
	// ──────────────────────────────────────────────────────────

	@Test
	fun `ACK_ACK is the correct wire token`() {
		assertContentEquals("+ACK".encodeToByteArray(), ACK_ACK)
	}

	@Test
	fun `ACK_NAK is the correct wire token`() {
		assertContentEquals("-NAK".encodeToByteArray(), ACK_NAK)
	}

	@Test
	fun `ACK_PROGRESS is the correct wire token`() {
		assertContentEquals("+WPI".encodeToByteArray(), ACK_PROGRESS)
	}

	@Test
	fun `ACK_TERM is the correct wire token`() {
		assertContentEquals("+TERM".encodeToByteArray(), ACK_TERM)
	}
}

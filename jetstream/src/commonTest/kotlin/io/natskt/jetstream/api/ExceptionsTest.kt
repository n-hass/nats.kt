package io.natskt.jetstream.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExceptionsTest {
	// ──────────────────────────────────────────────────────────
	// JetStreamPullStatusException hierarchy
	// ──────────────────────────────────────────────────────────

	@Test
	fun `JetStreamConsumerStateException stores code and description`() {
		val ex = JetStreamConsumerStateException(409, "Consumer Deleted")
		assertEquals(409, ex.code)
		assertEquals("Consumer Deleted", ex.description)
	}

	@Test
	fun `JetStreamConsumerStateException message includes code and description`() {
		val ex = JetStreamConsumerStateException(409, "Consumer Deleted")
		assertEquals("409 Consumer Deleted", ex.message)
	}

	@Test
	fun `JetStreamConsumerStateException message is just code when description is null`() {
		val ex = JetStreamConsumerStateException(409, null)
		assertEquals("409", ex.message)
	}

	@Test
	fun `JetStreamConsumerStateException with null description stores null`() {
		val ex = JetStreamConsumerStateException(409, null)
		assertNull(ex.description)
	}

	@Test
	fun `JetStreamConnectivityException stores code 503`() {
		val ex = JetStreamConnectivityException(503, "No Responders")
		assertEquals(503, ex.code)
		assertEquals("No Responders", ex.description)
	}

	@Test
	fun `JetStreamConnectivityException message includes code and description`() {
		val ex = JetStreamConnectivityException(503, "No Responders")
		assertEquals("503 No Responders", ex.message)
	}

	@Test
	fun `JetStreamConnectivityException with null description`() {
		val ex = JetStreamConnectivityException(503, null)
		assertNull(ex.description)
		assertEquals("503", ex.message)
	}

	@Test
	fun `JetStreamHeartbeatLostException always has code 100`() {
		val ex = JetStreamHeartbeatLostException()
		assertEquals(100, ex.code)
	}

	@Test
	fun `JetStreamHeartbeatLostException stores description`() {
		val ex = JetStreamHeartbeatLostException("heartbeat missed")
		assertEquals("heartbeat missed", ex.description)
		assertEquals("100 heartbeat missed", ex.message)
	}

	@Test
	fun `JetStreamHeartbeatLostException with null description`() {
		val ex = JetStreamHeartbeatLostException(null)
		assertNull(ex.description)
		assertEquals("100", ex.message)
	}

	@Test
	fun `JetStreamHeartbeatLostException default constructor has null description`() {
		val ex = JetStreamHeartbeatLostException()
		assertNull(ex.description)
	}

	// ──────────────────────────────────────────────────────────
	// Type hierarchy relationships
	// ──────────────────────────────────────────────────────────

	@Test
	fun `JetStreamConsumerStateException is a JetStreamPullStatusException`() {
		val ex: JetStreamPullStatusException = JetStreamConsumerStateException(409, "desc")
		assertIs<JetStreamPullStatusException>(ex)
	}

	@Test
	fun `JetStreamConnectivityException is a JetStreamPullStatusException`() {
		val ex: JetStreamPullStatusException = JetStreamConnectivityException(503, "desc")
		assertIs<JetStreamPullStatusException>(ex)
	}

	@Test
	fun `JetStreamHeartbeatLostException is a JetStreamPullStatusException`() {
		val ex: JetStreamPullStatusException = JetStreamHeartbeatLostException()
		assertIs<JetStreamPullStatusException>(ex)
	}

	@Test
	fun `JetStreamPullStatusException is a JetStreamException`() {
		val ex: JetStreamException = JetStreamConsumerStateException(409, "test")
		assertIs<JetStreamException>(ex)
	}

	@Test
	fun `JetStreamException is open and can be subclassed`() {
		// Verify JetStreamException is now open (was previously not)
		val ex = object : JetStreamException("custom") {}
		assertEquals("custom", ex.message)
	}

	// ──────────────────────────────────────────────────────────
	// when-expression exhaustiveness via sealed class
	// ──────────────────────────────────────────────────────────

	@Test
	fun `sealed JetStreamPullStatusException can be matched exhaustively`() {
		fun describeException(ex: JetStreamPullStatusException): String =
			when (ex) {
				is JetStreamConsumerStateException -> "state"
				is JetStreamConnectivityException -> "connectivity"
				is JetStreamHeartbeatLostException -> "heartbeat"
			}

		assertEquals("state", describeException(JetStreamConsumerStateException(409, null)))
		assertEquals("connectivity", describeException(JetStreamConnectivityException(503, null)))
		assertEquals("heartbeat", describeException(JetStreamHeartbeatLostException()))
	}

	@Test
	fun `JetStreamConsumerStateException with empty description string`() {
		// Edge case: empty string description (not null) - the message format differs
		val ex = JetStreamConsumerStateException(409, "")
		assertEquals("409 ", ex.message)
		assertEquals("", ex.description)
	}
}
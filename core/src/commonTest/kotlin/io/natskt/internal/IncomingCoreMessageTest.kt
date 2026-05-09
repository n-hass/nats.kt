@file:OptIn(InternalNatsApi::class)

package io.natskt.internal

import io.natskt.api.internal.InternalNatsApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncomingCoreMessageTest {
	private fun message(
		status: Int? = null,
		statusDescription: String? = null,
		sid: String = "1",
		subject: String = "test.subject",
		replyTo: String? = null,
		data: ByteArray? = null,
		headers: Map<String, List<String>>? = null,
	) = IncomingCoreMessage(
		sid = sid,
		subjectString = subject,
		replyToString = replyTo,
		data = data,
		headers = headers,
		status = status,
		statusDescription = statusDescription,
	)

	@Test
	fun `statusDescription defaults to null`() {
		val msg = IncomingCoreMessage(
			sid = "1",
			subjectString = "test",
			replyToString = null,
			data = null,
			headers = null,
			status = null,
		)
		assertNull(msg.statusDescription)
	}

	@Test
	fun `statusDescription is stored and retrieved`() {
		val msg = message(status = 404, statusDescription = "No Messages")
		assertEquals("No Messages", msg.statusDescription)
		assertEquals(404, msg.status)
	}

	@Test
	fun `equals returns true for messages with same statusDescription`() {
		val a = message(status = 404, statusDescription = "No Messages", data = byteArrayOf(1))
		val b = message(status = 404, statusDescription = "No Messages", data = byteArrayOf(1))
		assertEquals(a, b)
	}

	@Test
	fun `equals returns false when statusDescription differs`() {
		val a = message(status = 409, statusDescription = "Consumer Deleted")
		val b = message(status = 409, statusDescription = "Consumer Paused")
		assertNotEquals(a, b)
	}

	@Test
	fun `equals returns false when one statusDescription is null`() {
		val withDesc = message(status = 404, statusDescription = "No Messages")
		val withoutDesc = message(status = 404, statusDescription = null)
		assertNotEquals(withDesc, withoutDesc)
	}

	@Test
	fun `equals returns true for both having null statusDescription`() {
		val a = message(status = null, statusDescription = null)
		val b = message(status = null, statusDescription = null)
		assertEquals(a, b)
	}

	@Test
	fun `hashCode differs when statusDescription differs`() {
		val a = message(status = 409, statusDescription = "Consumer Deleted")
		val b = message(status = 409, statusDescription = "Consumer Paused")
		// Not strictly required by the contract, but should differ in practice
		assertNotEquals(a.hashCode(), b.hashCode())
	}

	@Test
	fun `hashCode is consistent for same message`() {
		val msg = message(status = 200, statusDescription = "OK")
		assertEquals(msg.hashCode(), msg.hashCode())
	}

	@Test
	fun `hashCode differs between null and non-null statusDescription`() {
		val withDesc = message(status = 404, statusDescription = "No Messages")
		val withoutDesc = message(status = 404, statusDescription = null)
		assertNotEquals(withDesc.hashCode(), withoutDesc.hashCode())
	}

	@Test
	fun `equals is reflexive`() {
		val msg = message(status = 503, statusDescription = "No Responders")
		assertEquals(msg, msg)
	}

	@Test
	fun `equals is symmetric`() {
		val a = message(status = 408, statusDescription = "Request Timeout")
		val b = message(status = 408, statusDescription = "Request Timeout")
		assertEquals(a, b)
		assertEquals(b, a)
	}

	@Test
	fun `subject is accessible via lazy property`() {
		val msg = message(subject = "test.subject.foo")
		assertEquals("test.subject.foo", msg.subject.raw)
	}

	@Test
	fun `replyTo is null when replyToString is null`() {
		val msg = message(replyTo = null)
		assertNull(msg.replyTo)
	}

	@Test
	fun `replyTo is accessible when replyToString is set`() {
		val msg = message(replyTo = "inbox.reply")
		assertEquals("inbox.reply", msg.replyTo?.raw)
	}

	@Test
	fun `multi-word statusDescription round-trips correctly`() {
		val msg = message(status = 409, statusDescription = "Consumer Deleted by Admin")
		assertEquals("Consumer Deleted by Admin", msg.statusDescription)
	}
}
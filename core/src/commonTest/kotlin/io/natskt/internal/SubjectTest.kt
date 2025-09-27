package io.natskt.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubjectTest {
	@Test
	fun `from creates subject when token is valid`() {
		val subject = Subject.from("demo.token")

		assertEquals("demo.token", subject.raw)
		assertFalse(validateSubject(subject.raw))
	}

	@Test
	fun `fromOrNull rejects invalid characters`() {
		assertEquals(null, Subject.fromOrNull("has space"))
		assertEquals(null, Subject.fromOrNull("has\u0000null"))
	}

	@Test
	fun `validateSubject reports invalid tokens`() {
		assertTrue(validateSubject("with space"))
		assertTrue(validateSubject("\u0000binary"))
		assertFalse(validateSubject("NATS"))
	}
}

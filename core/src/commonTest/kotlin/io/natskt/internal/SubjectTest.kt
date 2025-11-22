@file:OptIn(InternalNatsApi::class)

package io.natskt.internal

import io.natskt.api.Subject
import io.natskt.api.from
import io.natskt.api.fromOrNull
import io.natskt.api.internal.InternalNatsApi
import io.natskt.api.isInvalidSubject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubjectTest {
	@Test
	fun `from creates subject when token is valid`() {
		val subject = Subject.from("demo.token")

		assertEquals("demo.token", subject.raw)
		assertFalse(isInvalidSubject(subject.raw))
	}

	@Test
	fun `fromOrNull rejects invalid characters`() {
		assertEquals(null, Subject.fromOrNull("has space"))
		assertEquals(null, Subject.fromOrNull("has\u0000null"))
	}

	@Test
	fun `validateSubject reports invalid tokens`() {
		assertTrue(isInvalidSubject("with space"))
		assertTrue(isInvalidSubject("\u0000binary"))
		assertFalse(isInvalidSubject("NATS"))
	}
}

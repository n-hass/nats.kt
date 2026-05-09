@file:OptIn(InternalNatsApi::class)

package io.natskt.internal

import io.natskt.api.InvalidSubjectException
import io.natskt.api.Subject
import io.natskt.api.SubjectToken
import io.natskt.api.from
import io.natskt.api.fromOrNull
import io.natskt.api.fullyQualified
import io.natskt.api.internal.InternalNatsApi
import io.natskt.api.isInvalidFullyQualifiedSubject
import io.natskt.api.isInvalidSubject
import io.natskt.api.isInvalidToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
		assertEquals(null, Subject.fromOrNull("has\tnull"))
	}

	@Test
	fun `subject rejects whitespace and control chars`() {
		assertTrue(isInvalidSubject("with space"))
		assertTrue(isInvalidSubject("tab\there"))
		assertTrue(isInvalidSubject("cr\rhere"))
		assertTrue(isInvalidSubject("lf\nhere"))
		assertFalse(isInvalidSubject("NATS"))
	}

	@Test
	fun `subject rejects empty tokens`() {
		assertTrue(isInvalidSubject(""))
		assertTrue(isInvalidSubject("."))
		assertTrue(isInvalidSubject(".leading"))
		assertTrue(isInvalidSubject("trailing."))
		assertTrue(isInvalidSubject("empty..middle"))
	}

	@Test
	fun `subject permits utf8 tokens`() {
		assertFalse(isInvalidSubject("événements.foo"))
		assertFalse(isInvalidSubject("emoji.😀.suffix"))
		assertFalse(isInvalidSubject("ключ.значение"))
	}

	@Test
	fun `subject permits wildcards`() {
		assertFalse(isInvalidSubject("orders.*"))
		assertFalse(isInvalidSubject("orders.>"))
		assertFalse(isInvalidSubject("orders.*.created"))
	}

	@Test
	fun `fullyQualified rejects wildcards`() {
		assertTrue(isInvalidFullyQualifiedSubject("orders.*"))
		assertTrue(isInvalidFullyQualifiedSubject("orders.>"))
		assertFalse(isInvalidFullyQualifiedSubject("orders.created"))

		assertFailsWith<InvalidSubjectException> { Subject.fullyQualified("orders.>") }
	}

	@Test
	fun `token rejects whitespace dots wildcards and slashes`() {
		assertTrue(isInvalidToken(""))
		assertTrue(isInvalidToken("with space"))
		assertTrue(isInvalidToken("dot.in.token"))
		assertTrue(isInvalidToken("star*"))
		assertTrue(isInvalidToken("gt>"))
		assertTrue(isInvalidToken("path/with/slash"))
		assertTrue(isInvalidToken("back\\slash"))

		assertFalse(isInvalidToken("good_token-1"))
	}

	@Test
	fun `token permits utf8`() {
		assertFalse(isInvalidToken("événements"))
		assertFalse(isInvalidToken("😀"))

		val token = SubjectToken.from("événements")
		assertEquals("événements", token.token)
	}
}

package io.natskt.client

import io.natskt.internal.OutgoingMessage
import io.natskt.internal.Subject
import io.natskt.internal.from
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageBuilderTest {
	@Test
	fun `byte message builder requires subject`() {
		assertFailsWith<IllegalStateException> {
			ByteMessageBuilder().build()
		}
	}

	@Test
	fun `string message builder requires subject`() {
		assertFailsWith<IllegalStateException> {
			StringMessageBuilder().build()
		}
	}

	@Test
	fun `byte message builder constructs outgoing message`() {
		val payload = byteArrayOf(1, 2, 3)

		val message =
			ByteMessageBuilder()
				.apply {
					subject = "demo"
					replyTo = "inbox"
					headers = mapOf("Header" to listOf("value"))
					data = payload
				}.build()

		val expected =
			OutgoingMessage(
				subject = Subject.from("demo"),
				replyTo = Subject.from("inbox"),
				headers = mapOf("Header" to listOf("value")),
				data = payload,
			)

		assertEquals(expected.subject, message.subject)
		assertEquals(expected.replyTo, message.replyTo)
		assertEquals(expected.headers, message.headers)
		assertContentEquals(expected.data!!, message.data!!)
	}

	@Test
	fun `string message builder encodes payload`() {
		val message =
			StringMessageBuilder()
				.apply {
					subject = "demo"
					data = "hello"
				}.build()

		assertEquals(Subject.from("demo"), message.subject)
		assertContentEquals("hello".encodeToByteArray(), message.data!!)
		assertEquals(null, message.replyTo)
	}
}

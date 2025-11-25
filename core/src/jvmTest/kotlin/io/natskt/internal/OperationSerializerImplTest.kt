@file:OptIn(ExperimentalCoroutinesApi::class, InternalNatsApi::class)

package io.natskt.internal

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import io.natskt.api.internal.InternalNatsApi
import io.natskt.api.internal.OperationEncodeBuffer
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.fail

class OperationSerializerImplTest {
	private fun newSerializer(): OperationSerializerImpl = OperationSerializerImpl()

	private suspend fun channelOf(vararg chunks: ByteArray): ByteReadChannel {
		val ch = ByteChannel(autoFlush = true)
		chunks.forEach { ch.writeFully(it) }
		ch.close()
		return ch
	}

	private fun b(s: String) = s.encodeToByteArray()

	private class RecordingBuffer : OperationEncodeBuffer {
		private val data = mutableListOf<Byte>()

		override suspend fun writeByte(value: Byte) {
			data.add(value)
		}

		override suspend fun writeBytes(
			value: ByteArray,
			offset: Int,
			length: Int,
		) {
			for (i in offset until offset + length) {
				data.add(value[i])
			}
		}

		override suspend fun writeAscii(value: String) {
			value.encodeToByteArray().forEach { data.add(it) }
		}

		override suspend fun writeInt(value: Int) {
			writeAscii(value.toString())
		}

		override suspend fun writeCrLf() {
			data.add('\r'.code.toByte())
			data.add('\n'.code.toByte())
		}

		fun toByteArray(): ByteArray = data.toByteArray()
	}

	private suspend fun OperationSerializerImpl.encodeToBytes(op: ClientOperation): ByteArray {
		val buffer = RecordingBuffer()
		encode(op, buffer)
		return buffer.toByteArray()
	}

	@Test
	fun `parse - ping pong ok err`() =
		runTest {
			val ser = newSerializer()
			val ch =
				channelOf(
					b("PING\r\n"),
					b("PONG\r\n"),
					b("+OK\r\n"),
					b("-ERR 'x'\r\n"),
				)

			assertEquals(Operation.Ping, ser.parse(ch))
			assertEquals(Operation.Pong, ser.parse(ch))
			assertEquals(Operation.Ok, ser.parse(ch))
			assertEquals(Operation.Err("x"), ser.parse(ch))
		}

	@Test
	fun `parse info json payload`() =
		runTest {
			val ser = newSerializer()
			val infoJson =
				"""
				{
					"server_id": "abc",
					"server_name": "aonei",
					"version": "2.10.0",
					"go": "aonei",
					"host": "aonei",
					"port": 489,
					"proto": 1,
					"headers": true,
					"max_payload":1048576
				}
				""".trimIndent()
			val ch = channelOf(b("INFO $infoJson\r\n"))

			val op = ser.parse(ch)
			val info = op as? ServerOperation.InfoOp ?: fail("Expected InfoOp, got $op")
			assertEquals("abc", info.serverId)
			assertTrue(info.headers == true)
			assertTrue(info.maxPayload!! >= 1024)
		}

	@Test
	fun `parse message without reply`() =
		runTest {
			val ser = newSerializer()
			// MSG <subject> <sid> <n>\r\n[payload]\r\n
			val ch =
				channelOf(
					b("MSG s 9 4\r\n"),
					b("DATA"),
					b("\r\n"),
				)
			val op = ser.parse(ch)
			val msg = op as? IncomingCoreMessage ?: fail("Expected MsgOp")
			assertEquals("s", msg.subject.raw)
			assertEquals("9", msg.sid)
			assertNull(msg.replyTo)
			assertContentEquals(b("DATA"), msg.data)
		}

	@Test
	fun `parse message with reply`() =
		runTest {
			val ser = newSerializer()
			// MSG <subject> <sid> <reply-to> <n>\r\n[payload]\r\n
			val ch =
				channelOf(
					b("MSG sub 42 inbox 5\r\n"),
					b("HELLO"),
					b("\r\n"),
				)
			val op = ser.parse(ch)
			val msg = op as? IncomingCoreMessage ?: fail("Expected MsgOp")
			assertEquals("sub", msg.subject.raw)
			assertEquals("42", msg.sid)
			assertEquals("inbox", msg.replyTo?.raw)
			assertContentEquals(b("HELLO"), msg.data)
		}

	@Test
	fun `parse hmsg single header with payload`() =
		runTest {
			val ser = newSerializer()
			// HMSG <subject> <sid> <#hdr> <#total>\r\n
			// NATS/1.0\r\nHeader: X\r\n\r\nPAYLOAD\r\n
			val headerBlock = "NATS/1.0\r\nHeader: X\r\n\r\n"
			val payload = "PAYLOAD"
			val hdrLen = headerBlock.toByteArray().size
			val total = hdrLen + payload.length

			val ch =
				channelOf(
					b("HMSG s 1 $hdrLen $total\r\n"),
					b(headerBlock),
					b(payload),
					b("\r\n"),
				)

			val op = ser.parse(ch)
			val hm = op as? IncomingCoreMessage ?: fail("Expected HMsgOp")
			assertEquals("s", hm.subject.raw)
			assertEquals("1", hm.sid)
			assertNull(hm.replyTo)
			assertEquals(listOf("X"), hm.headers?.get("Header"))
			assertContentEquals(b(payload), hm.data)
		}

	@Test
	fun `parse hmsg with no payload, total = header bytes`() =
		runTest {
			val ser = newSerializer()
			// HMSG SUBJECT REPLY <hdr> <total==hdr>\r\n
			// NATS/1.0\r\nHeader: X\r\n\r\n\r\n
			val headerBlock = "NATS/1.0\r\nHeader: X\r\n\r\n"
			val hdrLen = headerBlock.toByteArray().size
			val total = hdrLen

			val ch =
				channelOf(
					b("HMSG SUBJECT 77 REPLY $hdrLen $total\r\n"),
					b(headerBlock),
					b("\r\n"),
				)

			val op = ser.parse(ch)
			val hm = op as? IncomingCoreMessage ?: fail("Expected HMsgOp")
			assertEquals("SUBJECT", hm.subject.raw)
			assertEquals("77", hm.sid)
			assertEquals("REPLY", hm.replyTo?.raw)
			assertEquals(listOf("X"), hm.headers?.get("Header"))
			assertNull(hm.data)
		}

	@Test
	fun `parse hmsg duplicate headers and payload`() =
		runTest {
			val ser = newSerializer()
			// NATS/1.0\r\nHeader1: X\r\nHeader1: Y\r\nHeader2: Z\r\n\r\nPAYLOAD\r\n
			val headerBlock = "NATS/1.0\r\nHeader1: X\r\nHeader1: Y\r\nHeader2: Z\r\n\r\n"
			val payload = "PAYLOAD"
			val hdrLen = headerBlock.toByteArray().size
			val total = hdrLen + payload.length

			val ch =
				channelOf(
					b("HMSG sub 2 $hdrLen $total\r\n"),
					b(headerBlock),
					b(payload),
					b("\r\n"),
				)

			val op = ser.parse(ch)
			val hm = op as? IncomingCoreMessage ?: fail("Expected HMsgOp")
			assertEquals(listOf("X", "Y"), hm.headers?.get("Header1"))
			assertEquals(listOf("Z"), hm.headers?.get("Header2"))
			assertContentEquals(b(payload), hm.data)
		}

	@Test
	fun `parse hmsg duplicate headers zero payload`() =
		runTest {
			val ser = newSerializer()
			val headerBlock = "NATS/1.0\r\nHeader1: X\r\nHeader1: Y\r\nHeader2: Z\r\n\r\n"
			val hdrLen = headerBlock.toByteArray().size
			val total = hdrLen

			val ch =
				channelOf(
					b("HMSG sub 3 $hdrLen $total\r\n"),
					b(headerBlock),
					b("\r\n"),
				)

			val op = ser.parse(ch)
			val hm = op as? IncomingCoreMessage ?: fail("Expected HMsgOp")
			assertEquals(listOf("X", "Y"), hm.headers?.get("Header1"))
			assertEquals(listOf("Z"), hm.headers?.get("Header2"))
			assertNull(hm.data)
		}

	@Test
	fun `missing trailing crlf throws`() =
		runTest {
			val ser = newSerializer()
			// Missing the final CRLF after payload -> readPayloadExact() should throw
			val ch =
				channelOf(
					b("MSG a 1 3\r\n"),
					b("abc"),
					// no \r\n
				)
			val ex = assertFails { ser.parse(ch) }
			assertTrue(ex.cause is java.io.EOFException)
		}

	@Test
	fun `hmsg invalid header preamble rejected`() =
		runTest {
			val ser = newSerializer()
			val badHeader = "NOTS/1.0\r\nK: V\r\n\r\n"
			val hdrLen = badHeader.toByteArray().size
			val total = hdrLen
			val ch =
				channelOf(
					b("HMSG s 9 $hdrLen $total\r\n"),
					b(badHeader),
					b("\r\n"),
				)
			val ex = assertFails { ser.parse(ch) }
			assertTrue(ex.message?.contains("invalid NATS header preamble") == true)
		}

	@Test
	fun `encode hpub with headers and payload`() =
		runTest {
			val serializer = newSerializer()

			val payload = "DATA".encodeToByteArray()
			val headers =
				linkedMapOf(
					"X" to listOf("1", "2"),
					"Y" to emptyList(),
				)

			val encoded =
				serializer.encodeToBytes(
					ClientOperation.HPubOp(
						subject = "sub",
						replyTo = "reply",
						headers = headers,
						payload = payload,
					),
				)

			val expectedHeaderBlock = "NATS/1.0\r\nX: 1\r\nX: 2\r\nY: \r\n\r\n".encodeToByteArray()
			val expectedHeaderSize = expectedHeaderBlock.size
			val expectedTotalSize = expectedHeaderSize + payload.size

			val expected =
				buildString {
					append("HPUB sub reply ")
					append(expectedHeaderSize)
					append(" ")
					append(expectedTotalSize)
					append("\r\n")
				}.encodeToByteArray() + expectedHeaderBlock + payload + "\r\n".encodeToByteArray()

			assertContentEquals(expected, encoded)
		}

	@Test
	fun `encode hpub without headers or payload`() =
		runTest {
			val serializer = newSerializer()

			val encoded =
				serializer.encodeToBytes(
					ClientOperation.HPubOp(
						subject = "sub",
						replyTo = null,
						headers = emptyMap(),
						payload = null,
					),
				)

			val expectedHeaderBlock = "NATS/1.0\r\n\r\n".encodeToByteArray()
			val expectedHeaderSize = expectedHeaderBlock.size

			val expected =
				buildString {
					append("HPUB sub ")
					append(expectedHeaderSize)
					append(" ")
					append(expectedHeaderSize)
					append("\r\n")
				}.encodeToByteArray() + expectedHeaderBlock + "\r\n".encodeToByteArray()

			assertContentEquals(expected, encoded)
		}

	@Test
	fun `parses jetstream HMSG with a status code in the header preamble with headers after`() =
		runTest {
			val ch =
				channelOf(
					// initial block
					b("HMSG _INBOX.YgVlzDS5xpd3E9mZztFfBU.E9mZztFfEW 2 81 81"),
					b("\r\n"),
					b("NATS/1.0 408 Request Timeout\r\nNats-Pending-Messages: 1\r\nNats-Pending-Bytes: 0\r\n\r\n"),
					b("\r\n"),
				)

			val serializer = newSerializer()

			val op = serializer.parse(ch)
			val hm = op as? IncomingCoreMessage ?: fail("Expected HMsgOp")
			assertEquals(408, hm.status)
		}

	@Test
	fun `parses jetstream HMSG with a status code in the header preamble, with no headers following`() =
		runTest {
			val ch =
				channelOf(
					// initial block
					b("HMSG _INBOX.0896FfveaU22pvVeb1zo3R.pvVeb1zo6u 1 28 28"),
					b("\r\n"),
					b("NATS/1.0 404 No Messages\r\n\r\n"),
					b("\r\n"),
				)

			val serializer = newSerializer()

			val op = serializer.parse(ch)
			val hm = op as? IncomingCoreMessage ?: fail("Expected HMsgOp")
			assertEquals(404, hm.status)
		}
}

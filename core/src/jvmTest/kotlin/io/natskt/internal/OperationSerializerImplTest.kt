@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.natskt.internal

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import io.natskt.api.internal.Operation
import io.natskt.api.internal.ServerOperation
import junit.framework.TestCase.assertTrue
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

	@Test
	fun parse_ping_pong_ok_err() =
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
			assertEquals(Operation.Err("'x'"), ser.parse(ch))
			assertNull(ser.parse(ch))
		}

	@Test
	fun parse_info_json() =
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
	fun parse_msg_without_reply() =
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
			val msg = op as? ServerOperation.MsgOp ?: fail("Expected MsgOp")
			assertEquals("s", msg.subject)
			assertEquals("9", msg.sid)
			assertNull(msg.replyTo)
			assertEquals(4, msg.bytes)
			assertContentEquals(b("DATA"), msg.payload)
		}

	@Test
	fun parse_msg_with_reply() =
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
			val msg = op as? ServerOperation.MsgOp ?: fail("Expected MsgOp")
			assertEquals("sub", msg.subject)
			assertEquals("42", msg.sid)
			assertEquals("inbox", msg.replyTo)
			assertEquals(5, msg.bytes)
			assertContentEquals(b("HELLO"), msg.payload)
		}

	@Test
	fun parse_hmsg_single_header_with_payload() =
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
			val hm = op as? ServerOperation.HMsgOp ?: fail("Expected HMsgOp")
			assertEquals("s", hm.subject)
			assertEquals("1", hm.sid)
			assertNull(hm.replyTo)
			assertEquals(hdrLen, hm.headerBytes)
			assertEquals(total, hm.totalBytes)
			assertEquals(listOf("X"), hm.headers?.get("Header"))
			assertContentEquals(b(payload), hm.payload)
		}

	@Test
	fun parse_hmsg_zero_payload_total_equals_hdr() =
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
			val hm = op as? ServerOperation.HMsgOp ?: fail("Expected HMsgOp")
			assertEquals("SUBJECT", hm.subject)
			assertEquals("77", hm.sid)
			assertEquals("REPLY", hm.replyTo)
			assertEquals(hdrLen, hm.headerBytes)
			assertEquals(total, hm.totalBytes)
			assertEquals(listOf("X"), hm.headers?.get("Header"))
			assertNull(hm.payload)
		}

	@Test
	fun parse_hmsg_duplicate_headers_and_payload() =
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
			val hm = op as? ServerOperation.HMsgOp ?: fail("Expected HMsgOp")
			assertEquals(listOf("X", "Y"), hm.headers?.get("Header1"))
			assertEquals(listOf("Z"), hm.headers?.get("Header2"))
			assertContentEquals(b(payload), hm.payload)
		}

	@Test
	fun parse_hmsg_duplicate_headers_zero_payload() =
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
			val hm = op as? ServerOperation.HMsgOp ?: fail("Expected HMsgOp")
			assertEquals(listOf("X", "Y"), hm.headers?.get("Header1"))
			assertEquals(listOf("Z"), hm.headers?.get("Header2"))
			assertNull(hm.payload)
		}

	@Test
	fun msg_missing_trailing_crlf_throws() =
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
	fun hmsg_invalid_header_preamble_rejected() =
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
}

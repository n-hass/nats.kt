@file:OptIn(InternalNatsApi::class)

package io.natskt.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.read
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.natskt.api.ProtocolException
import io.natskt.api.internal.InternalNatsApi
import io.natskt.api.internal.OperationEncodeBuffer
import io.natskt.api.internal.OperationSerializer
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

private val logger = KotlinLogging.logger { }

private const val LINE_END = "\r\n"

private val PING = "PING".toByteArray()
private val PONG = "PONG".toByteArray()
private val OK = "+OK".toByteArray()
private val ERR = "-ERR".toByteArray()
private val INFO = "INFO".toByteArray()
private val MSG = "MSG".toByteArray()
private val HMSG = "HMSG".toByteArray()
private const val HEADER_START = "NATS/1.0"
private const val DOUBLE_LINE_END = "$LINE_END$LINE_END"
private val pingOpBytes = "PING".toByteArray()
private val pongOpBytes = "PONG".toByteArray()
private val connectOpBytes = "CONNECT ".toByteArray()
private val pubOpBytes = "PUB ".toByteArray()
private val hpubOpBytes = "HPUB ".toByteArray()
private val subOpBytes = "SUB ".toByteArray()
private val unsubOpBytes = "UNSUB ".toByteArray()

private val empty = "".toByteArray()

private const val SPACE_BYTE = ' '.code.toByte()
internal const val CR_BYTE = '\r'.code.toByte()
internal const val LF_BYTE = '\n'.code.toByte()
private const val CR_CODE = '\r'.code.toLong()
private const val LF_CODE = '\n'.code.toLong()
private val HEADER_START_BYTES = HEADER_START.encodeToByteArray()
internal val LINE_END_BYTES = LINE_END.encodeToByteArray()
private val COLON_SPACE_BYTES = ": ".encodeToByteArray()

internal class OperationSerializerImpl(
	private val maxControlLineBytes: Int,
	private val maxPayloadBytes: Int,
) : OperationSerializer {
	override suspend fun parse(channel: ByteReadChannel): ParsedOutput? {
		val line = channel.readControlLine(maxControlLineBytes)

		val start = line.firstToken()

		return when {
			start.contentEquals(PONG) -> return Operation.Pong
			start.contentEquals(PING) -> return Operation.Ping
			start.contentEquals(OK) -> return Operation.Ok
			start.contentEquals(ERR) -> {
				Operation.Err(
					message =
						if (line.size > 5) {
							line.copyOfRange(6, line.size - 1).decodeToString()
						} else {
							null
						},
				)
			}
			start.contentEquals(INFO) -> {
				val json = line.copyOfRange(5, line.size).decodeToString()
				return try {
					wireJsonFormat.decodeFromString<ServerOperation.InfoOp>(json)
				} catch (t: Throwable) {
					logger.warn(t) { "invalid INFO JSON: $json" }
					null
				}
			}
			start.contentEquals(MSG) -> {
				val parts = line.decodeToString().split(' ')
				return when (parts.size) {
					4 -> {
						val subject = parts[1]
						val sid = parts[2]
						val bytes = parts[3].toInt()
						if (bytes > maxPayloadBytes) {
							logger.error { "MSG payload ignored - will exceed maximum bytes $maxPayloadBytes" }
							return null
						}
						val payload = channel.readPayload(bytes)
						IncomingCoreMessage(
							sid = sid,
							subjectString = subject,
							replyToString = null,
							data = payload,
							headers = null,
							status = null,
						)
					}
					5 -> {
						val subject = parts[1]
						val sid = parts[2]
						val replyTo = parts[3]
						val bytes = parts[4].toInt()
						if (bytes > maxPayloadBytes) {
							logger.error { "MSG payload ignored - will exceed maximum bytes $maxPayloadBytes" }
							return null
						}
						val payload = channel.readPayload(bytes)
						IncomingCoreMessage(
							sid = sid,
							subjectString = subject,
							replyToString = replyTo,
							data = payload,
							headers = null,
							status = null,
						)
					}
					else -> {
						logger.warn { "Unparseable MSG control line: ${parts.joinToString(" ")}" }
						return null
					}
				}
			}

			start.contentEquals(HMSG) -> {
				val parts = line.decodeToString().split(' ')
				return when (parts.size) {
					5 -> {
						val subject = parts[1]
						val sid = parts[2]
						val hdrBytes = parts[3].toInt()
						val totalBytes = parts[4].toInt()
						if (totalBytes > maxPayloadBytes) {
							logger.error { "HMSG payload ignored - will exceed maximum bytes $maxPayloadBytes" }
							return null
						}
						require(hdrBytes >= 0 && totalBytes >= hdrBytes) {
							"invalid HMSG sizes: hdr=$hdrBytes total=$totalBytes"
						}

						val hdrBlock = channel.readExact(hdrBytes)
						val hdrString = hdrBlock.decodeToString()
						val payloadLen = totalBytes - hdrBytes
						val payload =
							if (payloadLen > 0) {
								channel.readPayload(payloadLen)
							} else {
								// still need to consume trailing CRLF even when payload is empty
								val c = channel.readByte()
								val l = channel.readByte()
								require(c == CR_BYTE && l == LF_BYTE) { "malformed HMSG terminator" }
								null
							}
						IncomingCoreMessage(
							sid = sid,
							subjectString = subject,
							replyToString = null,
							headers = parseHeaders(hdrString),
							data = payload,
							status = parseStatusCode(hdrString),
						)
					}
					6 -> {
						val subject = parts[1]
						val sid = parts[2]
						val replyTo = parts[3]
						val hdrBytes = parts[4].toInt()
						val totalBytes = parts[5].toInt()
						if (totalBytes > maxPayloadBytes) {
							logger.error { "HMSG payload ignored - will exceed maximum bytes $maxPayloadBytes" }
							return null
						}
						require(hdrBytes >= 0 && totalBytes >= hdrBytes) {
							"invalid HMSG sizes: hdr=$hdrBytes total=$totalBytes"
						}

						val hdrBlock = channel.readExact(hdrBytes) // includes trailing CRLF CRLF
						val hdrString = hdrBlock.decodeToString()
						val payloadLen = totalBytes - hdrBytes
						val payload =
							if (payloadLen > 0) {
								channel.readPayload(payloadLen)
							} else {
								val c = channel.readByte()
								val l = channel.readByte()
								require(c == CR_BYTE && l == LF_BYTE) { "malformed HMSG terminator" }
								null
							}

						IncomingCoreMessage(
							sid = sid,
							subjectString = subject,
							replyToString = replyTo,
							headers = parseHeaders(hdrString),
							data = payload,
							status = parseStatusCode(hdrString),
						)
					}
					else -> {
						logger.warn { "Unparseable MSG control line: ${parts.joinToString(" ")}" }
						return null
					}
				}
			}

			start.contentEquals(empty) -> {
				Operation.Empty
			}

			else -> {
				logger.warn { "Unparseable control message: ${line.decodeToString()}" }
				null
			}
		}
	}

	override suspend fun encode(
		op: ClientOperation,
		buffer: OperationEncodeBuffer,
	) {
		when (op) {
			Operation.Ping -> buffer.writeBytes(pingOpBytes)
			Operation.Pong -> buffer.writeBytes(pongOpBytes)
			is ClientOperation.ConnectOp -> {
				buffer.writeBytes(connectOpBytes)
				buffer.writeUtf8(wireJsonFormat.encodeToString(op))
			}
			is ClientOperation.PubOp -> {
				buffer.writeBytes(pubOpBytes)
				buffer.writeUtf8(op.subject)
				val replyTo = op.replyTo
				if (replyTo != null) {
					buffer.writeByte(SPACE_BYTE)
					buffer.writeUtf8(replyTo)
				}
				buffer.writeByte(SPACE_BYTE)
				val payloadBytes = op.payload
				if (payloadBytes == null) {
					buffer.writeByte('0'.code.toByte())
				} else {
					buffer.writeInt(payloadBytes.size)
				}
				buffer.writeCrLf()
				if (payloadBytes != null) {
					buffer.writeBytes(payloadBytes)
				}
			}
			is ClientOperation.HPubOp -> {
				val payloadBytes = op.payload
				val payloadLength = payloadBytes?.size ?: 0
				val headerSize = headersSize(op.headers)
				val totalLength = headerSize + payloadLength

				buffer.writeBytes(hpubOpBytes)
				buffer.writeUtf8(op.subject)
				val replyTo = op.replyTo
				if (replyTo != null) {
					buffer.writeByte(SPACE_BYTE)
					buffer.writeUtf8(replyTo)
				}
				buffer.writeByte(SPACE_BYTE)
				buffer.writeInt(headerSize)
				buffer.writeByte(SPACE_BYTE)
				buffer.writeInt(totalLength)
				buffer.writeCrLf()

				buffer.writeHeaders(op.headers)
				if (payloadBytes != null) {
					buffer.writeBytes(payloadBytes)
				}
			}
			is ClientOperation.SubOp -> {
				buffer.writeBytes(subOpBytes)
				buffer.writeUtf8(op.subject)
				buffer.writeByte(SPACE_BYTE)
				val queueGroup = op.queueGroup
				if (queueGroup != null) {
					buffer.writeUtf8(queueGroup)
					buffer.writeByte(SPACE_BYTE)
				}
				buffer.writeUtf8(op.sid)
			}
			is ClientOperation.UnSubOp -> {
				buffer.writeBytes(unsubOpBytes)
				buffer.writeUtf8(op.sid)
				val maxMsgs = op.maxMsgs
				if (maxMsgs != null) {
					buffer.writeByte(SPACE_BYTE)
					buffer.writeInt(maxMsgs)
				}
			}
		}
		buffer.writeCrLf()
	}
}

private suspend fun ByteReadChannel.readControlLine(maxLen: Int): ByteArray {
	val acc = Buffer()
	var last: Long = -1
	var found = false
	var total = 0

	while (!found) {
		awaitContent()
		read { buf, start, end ->
			var i = start
			while (i < end) {
				val b = buf[i].toLong()
				i++

				if (last == CR_CODE && b == LF_CODE) {
					// We just consumed CRLF; do not write them to acc
					found = true
					last = -1
					break
				}

				if (last != -1L) {
					acc.writeByte(last.toByte())
					total++
					if (total > maxLen) throw ProtocolException("control line exceeds $maxLen bytes")
				}
				last = b
			}
			i - start
		}

		if (isClosedForRead && !found) {
			// Channel closed before CRLF; flush the dangling 'last' if any and return what we have
			if (last != -1L && last != CR_CODE) {
				acc.writeByte(last.toByte())
			}
			break
		}
	}

	return acc.readByteArray()
}

private suspend fun ByteReadChannel.readPayload(n: Int): ByteArray {
	val out = ByteArray(n)
	readFully(out, 0, n)
	val c =
		try {
			readByte()
		} catch (e: Exception) {
			throw ProtocolException("missing payload CR terminator", e)
		}
	val l =
		try {
			readByte()
		} catch (e: Exception) {
			throw ProtocolException("missing payload LF terminator", e)
		}
	if (c != CR_BYTE || l != LF_BYTE) throw ProtocolException("malformed payload terminator")
	return out
}

private fun ByteArray.firstToken(): ByteArray {
	val sp = indexOf(SPACE_BYTE).let { if (it < 0) size else it }
	return copyOfRange(0, sp)
}

/**
 * Read exactly [n] bites without touching trailing line CRLF
 */
private suspend fun ByteReadChannel.readExact(n: Int): ByteArray {
	val out = ByteArray(n)
	readFully(out, 0, n)
	return out
}

private fun parseStatusCode(s: String): Int? {
	require(s.startsWith(HEADER_START)) { "invalid NATS header preamble" }
	val firstCrlf = s.indexOf(LINE_END)
	require(firstCrlf >= HEADER_START.length) { "invalid NATS header preamble" }
	val firstLine = s.take(firstCrlf)

	// Check if there's a status code after "NATS/1.0"
	if (firstLine.length > HEADER_START.length && firstLine[HEADER_START.length] == ' ') {
		val statusPart = firstLine.substring(HEADER_START.length + 1).trim()
		val statusCode = statusPart.split(' ').firstOrNull()?.toIntOrNull()
		return statusCode
	}
	return null
}

private fun parseHeaders(s: String): Map<String, List<String>>? {
	require(s.startsWith(HEADER_START)) { "invalid NATS header preamble" }
	// Find the first CRLF to handle optional status codes after NATS/1.0
	val firstCrlf = s.indexOf(LINE_END)
	require(firstCrlf >= HEADER_START.length) { "invalid NATS header preamble" }
	val start = firstCrlf + LINE_END.length

	// Check if there are any headers after the status line
	// If the remaining content after the first line is just CRLF, there are no headers
	if (start >= s.length || s.substring(start) == LINE_END) {
		return null
	}

	val end = s.lastIndexOf(DOUBLE_LINE_END).takeIf { it >= start } ?: error("headers missing terminating CRLF CRLF")
	val map = LinkedHashMap<String, MutableList<String>>()
	var i = start
	while (i < end) {
		val j = s.indexOf('\r', i)
		val lineEnd = if (j in i until end && j + 1 <= end && s.getOrNull(j + 1) == '\n') j else end
		if (lineEnd <= i) break // empty line (should not happen before end)
		val line = s.substring(i, lineEnd)
		val c = line.indexOf(':')
		require(c > 0) { "malformed header line: '$line'" }
		val name = line.take(c) // preserve case
		val value = line.substring(c + 1).trimStart() // strip leading space after colon
		if (map[name] == null) {
			map[name] = mutableListOf()
		}
		map[name]?.add(value)
		i = lineEnd + 2 // skip CRLF
	}
	return map.ifEmpty { null }
}

private fun headersSize(headers: Map<String, List<String>>?): Int {
	var length = HEADER_START_BYTES.size + LINE_END_BYTES.size + LINE_END_BYTES.size
	headers?.forEach { (name, values) ->
		val nameLength = utf8Length(name)
		if (values.isEmpty()) {
			length += nameLength + COLON_SPACE_BYTES.size + LINE_END_BYTES.size
		} else {
			values.forEach { value ->
				length += nameLength + COLON_SPACE_BYTES.size + utf8Length(value) + LINE_END_BYTES.size
			}
		}
	}
	return length
}

private fun utf8Length(s: String): Int {
	var len = 0
	var i = 0
	while (i < s.length) {
		val ch = s[i]
		when {
			ch.code < 0x80 -> len += 1
			ch.code < 0x800 -> len += 2
			ch in '\uD800'..'\uDBFF' && i + 1 < s.length && s[i + 1] in '\uDC00'..'\uDFFF' -> {
				len += 4
				i++ // skip low surrogate
			}
			else -> len += 3
		}
		i++
	}
	return len
}

private suspend fun OperationEncodeBuffer.writeHeaders(headers: Map<String, List<String>>?) {
	writeUtf8(HEADER_START)
	writeCrLf()
	headers?.forEach { (name, values) ->
		if (values.isEmpty()) {
			writeUtf8(name)
			writeBytes(COLON_SPACE_BYTES)
			writeCrLf()
		} else {
			values.forEach { value ->
				writeUtf8(name)
				writeBytes(COLON_SPACE_BYTES)
				writeUtf8(value)
				writeCrLf()
			}
		}
	}
	writeCrLf()
}

@file:OptIn(InternalNatsApi::class)

package io.natskt.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.TooLongLineException
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.read
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.natskt.api.internal.DEFAULT_MAX_CONTROL_LINE_BYTES
import io.natskt.api.internal.DEFAULT_MAX_PAYLOAD_BYTES
import io.natskt.api.internal.InternalNatsApi
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

private val pingOpBytes = "PING".toByteArray()
private val pongOpBytes = "PONG".toByteArray()
private val connectOpBytes = "CONNECT ".toByteArray()
private val pubOpBytes = "PUB ".toByteArray()
private val hpubOpBytes = "HPUB ".toByteArray()
private val subOpBytes = "SUB ".toByteArray()
private val unsubOpBytes = "UNSUB ".toByteArray()

private val empty = "".toByteArray()

private val lineEndBytes = LINE_END.toByteArray()
private val spaceByte = ' '.code.toByte()
private val crByte = '\r'.code.toByte()
private val lfByte = '\n'.code.toByte()
private val cr = '\r'.code.toLong()
private val lf = '\n'.code.toLong()

internal class OperationSerializerImpl(
	private val maxControlLineBytes: Int = DEFAULT_MAX_CONTROL_LINE_BYTES,
	private val maxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES,
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
						val payloadLen = totalBytes - hdrBytes
						val payload =
							if (payloadLen > 0) {
								channel.readPayload(payloadLen)
							} else {
								// still need to consume trailing CRLF even when payload is empty
								val c = channel.readByte()
								val l = channel.readByte()
								require(c == crByte && l == lfByte) { "malformed HMSG terminator" }
								null
							}
						IncomingCoreMessage(
							sid = sid,
							subjectString = subject,
							replyToString = null,
							headers = parseHeaders(hdrBlock),
							data = payload,
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
						val payloadLen = totalBytes - hdrBytes
						val payload =
							if (payloadLen > 0) {
								channel.readPayload(payloadLen)
							} else {
								val c = channel.readByte()
								val l = channel.readByte()
								require(c == crByte && l == lfByte) { "malformed HMSG terminator" }
								null
							}

						IncomingCoreMessage(
							sid = sid,
							subjectString = subject,
							replyToString = replyTo,
							headers = parseHeaders(hdrBlock),
							data = payload,
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

	override fun encode(op: ClientOperation): ByteArray =
		when (op) {
			Operation.Ping -> pingOpBytes
			Operation.Pong -> pongOpBytes
			is ClientOperation.ConnectOp -> connectOpBytes + wireJsonFormat.encodeToString(op).encodeToByteArray()
			is ClientOperation.PubOp -> {
				val payloadExists = op.payload != null

				var pub =
					pubOpBytes +
						buildString {
							append(op.subject)
							if (op.replyTo != null) {
								append(" ")
								append(op.replyTo)
							}
							if (payloadExists) {
								val payloadBytes = op.payload!!
								append(" ")
								append(payloadBytes.size)
							} else {
								append(" 0")
							}
						}.encodeToByteArray() + lineEndBytes

				if (payloadExists) {
					pub += op.payload!!
				}

				pub
			}
			is ClientOperation.HPubOp -> {
				val payloadBytes = op.payload
				val headerBytes =
					buildString {
						append(HEADER_START)
						op.headers?.forEach { (name, values) ->
							if (values.isEmpty()) {
								append(name)
								append(": ")
								append(LINE_END)
							} else {
								values.forEach { value ->
									append(name)
									append(": ")
									append(value)
									append(LINE_END)
								}
							}
						}
						append(LINE_END)
					}.encodeToByteArray()

				val payloadLength = payloadBytes?.size ?: 0
				val totalLength = headerBytes.size + payloadLength

				var hpub =
					hpubOpBytes +
						buildString {
							append(op.subject)
							if (op.replyTo != null) {
								append(" ")
								append(op.replyTo)
							}
							append(" ")
							append(headerBytes.size)
							append(" ")
							append(totalLength)
						}.encodeToByteArray() + lineEndBytes

				hpub += headerBytes

				if (payloadBytes != null) {
					hpub += payloadBytes
				}

				hpub
			}
			is ClientOperation.SubOp ->
				subOpBytes +
					buildString {
						append(op.subject)
						append(" ")
						if (op.queueGroup != null) {
							append(op.queueGroup)
							append(" ")
						}
						append(op.sid)
					}.encodeToByteArray()
			is ClientOperation.UnSubOp ->
				unsubOpBytes +
					buildString {
						append(op.sid)
						if (op.maxMsgs != null) {
							append(" ")
							append(op.maxMsgs)
						}
					}.encodeToByteArray()
		} + lineEndBytes
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

				if (last == cr && b == lf) {
					// We just consumed CRLF; do not write them to acc
					found = true
					last = -1
					break
				}

				if (last != -1L) {
					acc.writeByte(last.toByte())
					total++
					if (total > maxLen) throw TooLongLineException("line exceeds $maxLen bytes")
				}
				last = b
			}
			i - start
		}

		if (isClosedForRead && !found) {
			// Channel closed before CRLF; flush the dangling 'last' if any and return what we have
			if (last != -1L && last != cr) {
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
			throw IllegalStateException("missing payload CR terminator", e)
		}
	val l =
		try {
			readByte()
		} catch (e: Exception) {
			throw IllegalStateException("missing payload LF terminator", e)
		}
	require(c == crByte && l == lfByte) { "malformed payload terminator" }
	return out
}

private fun ByteArray.firstToken(): ByteArray {
	val sp = indexOf(spaceByte).let { if (it < 0) size else it }
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

private const val HEADER_START = "NATS/1.0$LINE_END"
private const val HEADER_START_LENGTH = HEADER_START.length
private const val DOUBLE_LINE_END = "$LINE_END$LINE_END"

private fun parseHeaders(raw: ByteArray): Map<String, List<String>>? {
	val s = raw.decodeToString()
	require(s.startsWith(HEADER_START)) { "invalid NATS header preamble" }
	val start = HEADER_START_LENGTH
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
		val name = line.substring(0, c) // preserve case
		val value = line.substring(c + 1).trimStart() // strip leading space after colon
		if (map[name] == null) {
			map[name] = mutableListOf()
		}
		map[name]?.add(value)
		i = lineEnd + 2 // skip CRLF
	}
	return map.ifEmpty { null }
}

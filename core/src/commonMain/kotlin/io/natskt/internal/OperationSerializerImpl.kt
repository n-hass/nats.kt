package io.natskt.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.TooLongLineException
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.read
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import io.natskt.api.internal.ClientOperation
import io.natskt.api.internal.DEFAULT_MAX_LINE_BYTES
import io.natskt.api.internal.Operation
import io.natskt.api.internal.OperationSerializer
import io.natskt.api.internal.ServerOperation
import kotlinx.io.Buffer
import kotlinx.io.readByteArray

private val logger = KotlinLogging.logger { }

private const val LINE_END = "\r\n"
private val lineEndBytes = LINE_END.toByteArray()

private const val PING = "PING"
private const val PONG = "PONG"
private const val OK = "+OK"
private const val ERR = "-ERR"
private const val INFO = "INFO"
private const val MSG = "MSG"
private const val HMSG = "HMSG"

internal class OperationSerializerImpl(
	private val maxPayloadBytes: Int,
) : OperationSerializer {
	override suspend fun parse(channel: ByteReadChannel): Operation? {
		val line = channel.readLine()

		val start = line.firstToken().decodeToString()

		logger.trace { "received $start" }

		return when (start) {
			PONG -> return Operation.Pong
			PING -> return Operation.Ping
			OK -> return Operation.Ok
			ERR -> {
				Operation.Err(
					message =
						if (line.size > 5) {
							line.copyOfRange(5, line.size).decodeToString()
						} else {
							null
						},
				)
			}
			INFO -> {
				val json = line.copyOfRange(5, line.size).decodeToString()
				return try {
					wireJsonFormat.decodeFromString<ServerOperation.InfoOp>(json)
				} catch (t: Throwable) {
					logger.warn(t) { "invalid INFO JSON: $json" }
					null
				}
			}
			MSG -> {
				val parts = line.decodeToString().split(' ')
				return when (parts.size) {
					4 -> {
						val subject = parts[1]
						val sid = parts[2]
						val bytes = parts[3].toInt()
						val payload = channel.readPayload(bytes)
						ServerOperation.MsgOp(
							subject = subject,
							sid = sid,
							replyTo = null,
							bytes = bytes,
							payload = payload,
						)
					}
					5 -> {
						val subject = parts[1]
						val sid = parts[2]
						val replyTo = parts[3]
						val bytes = parts[4].toInt()
						val payload = channel.readPayload(bytes)
						ServerOperation.MsgOp(
							subject = subject,
							sid = sid,
							replyTo = replyTo,
							bytes = bytes,
							payload = payload,
						)
					}
					else -> {
						logger.warn { "Unparseable MSG control line: ${parts.joinToString(" ")}" }
						return null
					}
				}
			}

			HMSG -> {
				val parts = line.decodeToString().split(' ')
				return when (parts.size) {
					5 -> {
						val subject = parts[1]
						val sid = parts[2]
						val hdrBytes = parts[3].toInt()
						val totalBytes = parts[4].toInt()
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
						ServerOperation.HMsgOp(
							subject = subject,
							sid = sid,
							replyTo = null,
							headerBytes = hdrBytes,
							totalBytes = totalBytes,
							headers = parseHeaders(hdrBlock),
							payload = payload,
						)
					}
					6 -> {
						val subject = parts[1]
						val sid = parts[2]
						val replyTo = parts[3]
						val hdrBytes = parts[4].toInt()
						val totalBytes = parts[5].toInt()
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

						ServerOperation.HMsgOp(
							subject = subject,
							sid = sid,
							replyTo = replyTo,
							headerBytes = hdrBytes,
							totalBytes = totalBytes,
							headers = parseHeaders(hdrBlock),
							payload = payload,
						)
					}
					else -> {
						logger.warn { "Unparseable MSG control line: ${parts.joinToString(" ")}" }
						return null
					}
				}
			}

			else -> {
				logger.warn { "Unparseable control message: ${line.decodeToString()}" }
				null
			}
		}
	}

	override fun encode(op: ClientOperation): ByteArray =
		when (op) {
			Operation.Ping -> "PING".toByteArray()
			Operation.Pong -> "PONG".toByteArray()
			is ClientOperation.ConnectOp -> {
				"CONNECT ${wireJsonFormat.encodeToString(op)}".toByteArray()
			}
			is ClientOperation.PubOp -> {
				val payloadExists = op.payload != null

				var pub =
					buildString {
						append("PUB ${op.subject}")
						if (op.replyTo != null) {
							append(" ${op.replyTo}")
						}
						if (payloadExists) {
							val payloadBytes = op.payload
							append(" ${payloadBytes.size}$LINE_END")
						} else {
							append(" 0$LINE_END")
						}
					}.toByteArray()

				if (payloadExists) {
					pub += op.payload
				}

				pub
			}
			is ClientOperation.HPubOp -> TODO()
			is ClientOperation.SubOp -> {
				buildString {
					append("SUB ${op.subject} ")
					if (op.queueGroup != null) {
						append("${op.queueGroup} ")
					}
					append(op.sid)
				}.toByteArray()
			}
			is ClientOperation.UnSubOp -> TODO()
		} + lineEndBytes
}

private val crByte = '\r'.code.toByte()
private val lfByte = '\n'.code.toByte()
private val cr = '\r'.code.toLong()
private val lf = '\n'.code.toLong()

private suspend fun ByteReadChannel.readLine(maxLen: Long = DEFAULT_MAX_LINE_BYTES): ByteArray {
	val acc = Buffer()
	var last: Long = -1
	var found = false
	var total: Long = 0

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
	val sp = indexOf(' '.code.toByte()).let { if (it < 0) size else it }
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

private fun parseHeaders(raw: ByteArray): Map<String, List<String>>? {
	val s = raw.decodeToString()
	require(s.startsWith("NATS/1.0$LINE_END")) { "invalid NATS header preamble" }
	val start = "NATS/1.0$LINE_END".length
	val end = s.lastIndexOf("$LINE_END$LINE_END").takeIf { it >= start } ?: error("headers missing terminating CRLF CRLF")
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

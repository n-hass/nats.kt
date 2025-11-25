package io.natskt.client.connection

import io.ktor.utils.io.writeFully
import io.natskt.api.internal.OperationEncodeBuffer
import io.natskt.client.transport.Transport
import io.natskt.internal.CR_BYTE
import io.natskt.internal.LF_BYTE

internal class TransportWriteBuffer(
	private val transport: Transport,
	capacity: Int,
) : OperationEncodeBuffer {
	private val buffer = ByteArray(capacity.coerceAtLeast(1))
	private var position = 0

	fun hasPendingBytesAtCapacity(): Boolean = position >= buffer.size

	override suspend fun writeByte(value: Byte) = writeByteFlush(value)

	private suspend inline fun writeByteFlush(value: Byte) {
		if (position == buffer.size) {
			flush()
		}
		buffer[position] = value
		position++
	}

	override suspend fun writeBytes(
		value: ByteArray,
		offset: Int,
		length: Int,
	) {
		require(offset >= 0 && length >= 0 && offset + length <= value.size) { "invalid slice" }
		var currentOffset = offset
		var remaining = length
		while (remaining > 0) {
			if (position == buffer.size) {
				flush()
			}
			val toCopy = minOf(remaining, buffer.size - position)
			value.copyInto(buffer, position, currentOffset, currentOffset + toCopy)
			position += toCopy
			currentOffset += toCopy
			remaining -= toCopy
			if (position == buffer.size) {
				flush()
			}
		}
	}

	override suspend fun writeUtf8(value: String) {
		var idx = 0
		val length = value.length
		while (idx < length) {
			val ch = value[idx]
			var codePoint = ch.code
			idx++

			if (ch.isHighSurrogate()) {
				if (idx < length) {
					val low = value[idx]
					if (low.isLowSurrogate()) {
						codePoint = ((ch.code - 0xd800) shl 10) + (low.code - 0xdc00) + 0x10000
						idx++
					} else {
						codePoint = 0xfffd
					}
				} else {
					codePoint = 0xfffd
				}
			} else if (ch.isLowSurrogate()) {
				codePoint = 0xfffd
			}

			when {
				codePoint < 0x80 -> writeByteFlush(codePoint.toByte())
				codePoint < 0x800 -> {
					writeByteFlush((0b11000000 or (codePoint shr 6)).toByte())
					writeByteFlush((0b10000000 or (codePoint and 0b00111111)).toByte())
				}
				codePoint < 0x10000 -> {
					writeByteFlush((0b11100000 or (codePoint shr 12)).toByte())
					writeByteFlush((0b10000000 or ((codePoint shr 6) and 0b00111111)).toByte())
					writeByteFlush((0b10000000 or (codePoint and 0b00111111)).toByte())
				}
				else -> {
					writeByteFlush((0b11110000 or (codePoint shr 18)).toByte())
					writeByteFlush((0b10000000 or ((codePoint shr 12) and 0b00111111)).toByte())
					writeByteFlush((0b10000000 or ((codePoint shr 6) and 0b00111111)).toByte())
					writeByteFlush((0b10000000 or (codePoint and 0b00111111)).toByte())
				}
			}
		}
	}

	override suspend fun writeInt(value: Int) {
		writeUtf8(value.toString())
	}

	override suspend fun writeCrLf() {
		writeByteFlush(CR_BYTE)
		writeByteFlush(LF_BYTE)
	}

	suspend fun flush() {
		if (position == 0) return
		transport.write { channel -> channel.writeFully(buffer, 0, position) }
		transport.flush()
		position = 0
	}
}

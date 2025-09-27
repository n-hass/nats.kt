package io.natskt.nkeys

internal object Base32 {
	private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
	private val DECODE_TABLE = IntArray(256) { -1 }

	init {
		for (i in ALPHABET.indices) {
			val upper = ALPHABET[i]
			DECODE_TABLE[upper.code] = i
			val lower = upper.lowercaseChar()
			DECODE_TABLE[lower.code] = i
		}
	}

	fun encode(input: ByteArray): String {
		if (input.isEmpty()) return ""

		val output = CharArray((input.size + 7) * 8 / 5)
		var buffer = 0
		var bitsLeft = 0
		var index = 0

		for (byte in input) {
			buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
			bitsLeft += 8

			while (bitsLeft >= 5) {
				bitsLeft -= 5
				val value = (buffer shr bitsLeft) and 0x1F
				output[index++] = ALPHABET[value]
			}
		}

		if (bitsLeft > 0) {
			val value = (buffer shl (5 - bitsLeft)) and 0x1F
			output[index++] = ALPHABET[value]
		}

		return output.concatToString(0, index)
	}

	fun decode(input: CharArray): ByteArray {
		if (input.isEmpty()) return ByteArray(0)

		val out = ByteArray((input.size * 5) / 8 + 1)
		var buffer = 0
		var bitsLeft = 0
		var index = 0

		for (char in input) {
			if (char.code >= DECODE_TABLE.size) continue
			val value = DECODE_TABLE[char.code]
			if (value == -1) continue

			buffer = (buffer shl 5) or value
			bitsLeft += 5

			if (bitsLeft >= 8) {
				bitsLeft -= 8
				out[index++] = ((buffer shr bitsLeft) and 0xFF).toByte()
			}
		}

		return out.copyOf(index)
	}
}

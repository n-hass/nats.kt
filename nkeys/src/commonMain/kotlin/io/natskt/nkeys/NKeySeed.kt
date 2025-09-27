package io.natskt.nkeys

import io.github.andreypfau.curve25519.ed25519.Ed25519
import io.github.andreypfau.curve25519.ed25519.Ed25519PrivateKey
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

public class NKeySeed private constructor(
	private val privateKey: Ed25519PrivateKey,
	public val type: NKeyType,
) {
	private val publicKeyBytes: ByteArray by lazy { privateKey.publicKey().toByteArray() }

	public val publicKey: String by lazy { encodePublicKey(type, publicKeyBytes) }

	public fun sign(payload: ByteArray): ByteArray = privateKey.sign(payload)

	@OptIn(ExperimentalEncodingApi::class)
	public fun signToBase64(payload: ByteArray): String = Base64.Default.encode(sign(payload))

	@OptIn(ExperimentalEncodingApi::class)
	public fun signNonce(nonce: String): String = signToBase64(nonce.encodeToByteArray())

	public fun rawSeed(): ByteArray = privateKey.seed()

	public companion object {
		private const val SEED_PREFIX: Int = 18 shl 3

		public fun parse(seed: String): NKeySeed {
			val payload = decodeWithChecksum(seed)
			if (payload.size < 2 + 32) {
				throw IllegalArgumentException("Seed payload is too short")
			}

			val prefixByte = payload[0].toInt() and 0xFF
			val seedPrefix = prefixByte and 0xF8
			if (seedPrefix != SEED_PREFIX) {
				throw IllegalArgumentException("Value is not an encoded NKey seed")
			}

			val typeByte = ((prefixByte and 0x07) shl 5) or ((payload[1].toInt() and 0xF8) ushr 3)
			val type = NKeyType.fromPrefix(typeByte) ?: throw IllegalArgumentException("Unknown NKey prefix: $typeByte")

			val body = payload.copyOfRange(2, payload.size)
			if (body.size != 32 && body.size != 64) {
				throw IllegalArgumentException("Unexpected seed length: ${body.size}")
			}

			val seedBytes = body.copyOfRange(0, 32)
			val privateKey = Ed25519.keyFromSeed(seedBytes)
			return NKeySeed(privateKey, type)
		}

		public fun encodePublicKey(
			type: NKeyType,
			publicKey: ByteArray,
		): String {
			if (publicKey.size != 32) {
				throw IllegalArgumentException("Public key must be 32 bytes")
			}
			val payload = ByteArray(1 + publicKey.size)
			payload[0] = type.prefix.toByte()
			publicKey.copyInto(payload, 1)
			return encodeWithChecksum(payload)
		}

		public fun encodeSeed(
			type: NKeyType,
			privateKey: Ed25519PrivateKey,
		): String {
			val seedBytes = privateKey.seed()
			val b1 = (SEED_PREFIX or (type.prefix shr 5)) and 0xFF
			val b2 = ((type.prefix and 0x1F) shl 3) and 0xFF
			val payload = ByteArray(2 + seedBytes.size)
			payload[0] = b1.toByte()
			payload[1] = b2.toByte()
			seedBytes.copyInto(payload, 2)
			return encodeWithChecksum(payload)
		}

		public fun decodePublicKey(value: String): Pair<NKeyType, ByteArray> {
			val payload = decodeWithChecksum(value)
			if (payload.isEmpty()) {
				throw IllegalArgumentException("Public key payload is empty")
			}
			val prefix = payload[0].toInt() and 0xFF
			val type = NKeyType.fromPrefix(prefix) ?: throw IllegalArgumentException("Unknown NKey prefix: $prefix")
			val key = payload.copyOfRange(1, payload.size)
			if (key.size != 32) {
				throw IllegalArgumentException("Public key must be 32 bytes")
			}
			return type to key
		}

		private fun encodeWithChecksum(payload: ByteArray): String {
			val crc = Crc16.compute(payload)
			val result = ByteArray(payload.size + 2)
			payload.copyInto(result)
			result[result.lastIndex - 1] = (crc and 0xFF).toByte()
			result[result.lastIndex] = ((crc ushr 8) and 0xFF).toByte()
			return Base32.encode(result)
		}

		private fun decodeWithChecksum(value: String): ByteArray {
			val decoded = Base32.decode(value.trim().toCharArray())
			if (decoded.size < 4) {
				throw IllegalArgumentException("Value is too short")
			}
			val payload = decoded.copyOf(decoded.size - 2)
			val checksum = decoded.copyOfRange(decoded.size - 2, decoded.size)
			val expected = Crc16.compute(payload)
			val actual = (checksum[0].toInt() and 0xFF) or ((checksum[1].toInt() and 0xFF) shl 8)
			if (expected != actual) {
				throw IllegalArgumentException("Checksum invalid")
			}
			return payload
		}
	}
}

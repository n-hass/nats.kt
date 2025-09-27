package io.natskt.nkeys

import io.github.andreypfau.curve25519.ed25519.Ed25519
import io.nats.nkey.NKey
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NKeyInteropTest {
	@Test
	fun `encodeSeed roundtrip through java nkey`() {
		val seedBytes = ByteArray(32) { it.toByte() }
		val privateKey = Ed25519.keyFromSeed(seedBytes)
		val encoded = NKeySeed.encodeSeed(NKeyType.User, privateKey)
		val javaKey = NKey.fromSeed(encoded.toCharArray())

		assertEquals(encoded, String(javaKey.seed))
		val expectedPublic = NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray())
		assertEquals(expectedPublic, String(javaKey.publicKey))

		val payload = "nkey interop payload".encodeToByteArray()
		val kotlinSignature = privateKey.sign(payload)
		val javaSignature = javaKey.sign(payload)
		assertContentEquals(kotlinSignature, javaSignature)
	}

	@Test
	fun `kotlin parses java seed`() {
		val deterministicSeed = ByteArray(32) { (it * 13 + 7).toByte() }
		val javaKey = NKey.createUser(DeterministicSecureRandom(deterministicSeed))
		val javaSeed = String(javaKey.seed)

		val parsed = NKeySeed.parse(javaSeed)
		val reencoded = NKeySeed.encodeSeed(parsed.type, Ed25519.keyFromSeed(parsed.rawSeed()))
		assertEquals(javaSeed, reencoded)
		assertEquals(String(javaKey.publicKey), parsed.publicKey)
	}

	private class DeterministicSecureRandom(
		private val pattern: ByteArray,
	) : SecureRandom() {
		private var offset = 0

		override fun nextBytes(bytes: ByteArray) {
			for (i in bytes.indices) {
				bytes[i] = pattern[offset % pattern.size]
				offset += 1
			}
		}
	}
}

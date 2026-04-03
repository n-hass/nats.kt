package io.natskt.nkeys

import io.nats.nkey.NKey
import kotlinx.coroutines.test.runTest
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class NKeyInteropTest {
	@Test
	fun `encode seed roundtrips through Java nkey`() =
		runTest {
			val seedBytes = ByteArray(32) { it.toByte() }
			val encoded = NKeySeed.encodeSeed(NKeyType.User, seedBytes)
			val javaKey = NKey.fromSeed(encoded.toCharArray())
			val parsed = NKeySeed.parse(encoded)

			assertEquals(encoded, String(javaKey.seed))
			assertEquals(parsed.getPublicKey(), String(javaKey.publicKey))

			val payload = "nkey interop payload".encodeToByteArray()
			val kotlinSignature = parsed.sign(payload)
			val javaSignature = javaKey.sign(payload)
			assertContentEquals(kotlinSignature, javaSignature)
		}

	@Test
	fun `Kotlin parses seed from Java library`() =
		runTest {
			val deterministicSeed = ByteArray(32) { (it * 13 + 7).toByte() }
			val javaKey = NKey.createUser(DeterministicSecureRandom(deterministicSeed))
			val javaSeed = String(javaKey.seed)

			val parsed = NKeySeed.parse(javaSeed)
			val reencoded = NKeySeed.encodeSeed(parsed.type, parsed.rawSeed())
			assertEquals(javaSeed, reencoded)
			assertEquals(String(javaKey.publicKey), parsed.getPublicKey())
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

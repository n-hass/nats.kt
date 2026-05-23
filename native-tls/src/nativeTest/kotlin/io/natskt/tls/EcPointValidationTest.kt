package io.natskt.tls

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import kotlin.test.Test
import kotlin.test.assertFailsWith

class EcPointValidationTest {
	@Test
	fun `decoder rejects off-curve P-256 point`() {
		val ecdh = CryptographyProvider.Default.get(ECDH)
		val decoder = ecdh.publicKeyDecoder(EC.Curve.P256)

		val validPoint =
			ecdh
				.keyPairGenerator(EC.Curve.P256)
				.generateKeyBlocking()
				.publicKey
				.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW)

		check(validPoint.size == 65 && validPoint[0] == 0x04.toByte()) {
			"Expected uncompressed P-256 encoding (0x04 || x || y), got ${validPoint.size} bytes starting 0x${validPoint[0].toUByte().toString(16)}"
		}
		decoder.decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, validPoint)

		// Flipping the low bit of y shifts y by 1, so y^2 != x^3 - 3x + b (mod p)
		// for any x outside a negligibly-small set: the point is off-curve.
		val offCurvePoint = validPoint.copyOf().also { it[64] = (it[64].toInt() xor 1).toByte() }
		assertFailsWith<Throwable> {
			decoder.decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, offCurvePoint)
		}
	}
}

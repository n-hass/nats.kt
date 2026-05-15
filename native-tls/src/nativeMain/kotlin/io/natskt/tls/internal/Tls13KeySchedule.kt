@file:OptIn(dev.whyoleg.cryptography.DelicateCryptographyApi::class)

package io.natskt.tls.internal

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA384

/**
 * TLS 1.3 key schedule using HKDF-Extract and HKDF-Expand-Label.
 * RFC 8446 Section 7.1
 */
internal class Tls13KeySchedule(
	private val hashAlg: dev.whyoleg.cryptography.CryptographyAlgorithmId<Digest>,
) {
	private val hmac = CryptographyProvider.Default.get(HMAC)
	val hashLength: Int = if (hashAlg == SHA384) 48 else 32

	/** HKDF-Extract(salt, ikm) = HMAC-Hash(salt, ikm) */
	fun extract(
		salt: ByteArray,
		ikm: ByteArray,
	): ByteArray {
		val key = hmac.keyDecoder(hashAlg).decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, salt)
		return key.signatureGenerator().generateSignatureBlocking(ikm)
	}

	/** HKDF-Expand(prk, info, length) using HMAC */
	fun expand(
		prk: ByteArray,
		info: ByteArray,
		length: Int,
	): ByteArray {
		val key = hmac.keyDecoder(hashAlg).decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, prk)
		val gen = key.signatureGenerator()

		val result = ByteArray(length)
		var offset = 0
		var t = ByteArray(0)
		var counter: Byte = 1

		while (offset < length) {
			t = gen.generateSignatureBlocking(t + info + byteArrayOf(counter))
			val toCopy = minOf(t.size, length - offset)
			t.copyInto(result, offset, 0, toCopy)
			offset += toCopy
			counter++
		}
		return result
	}

	/**
	 * HKDF-Expand-Label(Secret, Label, Context, Length)
	 * = HKDF-Expand(Secret, HkdfLabel, Length)
	 *
	 * HkdfLabel = uint16(length) || uint8(labelLen) || "tls13 " || Label || uint8(contextLen) || Context
	 */
	fun expandLabel(
		secret: ByteArray,
		label: String,
		context: ByteArray,
		length: Int,
	): ByteArray {
		val fullLabel = "tls13 $label".encodeToByteArray()
		val hkdfLabel = ByteArray(2 + 1 + fullLabel.size + 1 + context.size)
		hkdfLabel[0] = (length shr 8).toByte()
		hkdfLabel[1] = length.toByte()
		hkdfLabel[2] = fullLabel.size.toByte()
		fullLabel.copyInto(hkdfLabel, 3)
		hkdfLabel[3 + fullLabel.size] = context.size.toByte()
		context.copyInto(hkdfLabel, 3 + fullLabel.size + 1)
		return expand(secret, hkdfLabel, length)
	}

	/** Derive-Secret(Secret, Label, Messages) = HKDF-Expand-Label(Secret, Label, Hash(Messages), Hash.length) */
	fun deriveSecret(
		secret: ByteArray,
		label: String,
		transcriptHash: ByteArray,
	): ByteArray = expandLabel(secret, label, transcriptHash, hashLength)

	/** Derive traffic keys from a traffic secret */
	fun deriveTrafficKeys(
		trafficSecret: ByteArray,
		keyLength: Int,
		ivLength: Int,
	): TrafficKeys {
		val key = expandLabel(trafficSecret, "key", ByteArray(0), keyLength)
		val iv = expandLabel(trafficSecret, "iv", ByteArray(0), ivLength)
		return TrafficKeys(key, iv)
	}

	/** Compute Finished verify_data = HMAC(finished_key, transcript_hash) */
	fun finishedVerifyData(
		baseKey: ByteArray,
		transcriptHash: ByteArray,
	): ByteArray {
		val finishedKey = expandLabel(baseKey, "finished", ByteArray(0), hashLength)
		val key = hmac.keyDecoder(hashAlg).decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, finishedKey)
		return key.signatureGenerator().generateSignatureBlocking(transcriptHash)
	}

	/** Compute the full key schedule from ECDH shared secret */
	fun computeHandshakeSecrets(
		sharedSecret: ByteArray,
		helloTranscriptHash: ByteArray,
	): HandshakeSecrets {
		val emptyHash = hashEmpty()
		val zeros = ByteArray(hashLength)

		// Early secret (no PSK)
		val earlySecret = extract(zeros, zeros)

		// Derive the salt for the handshake secret
		val derivedSecret = deriveSecret(earlySecret, "derived", emptyHash)

		// Handshake secret
		val handshakeSecret = extract(derivedSecret, sharedSecret)

		val clientHandshakeTrafficSecret = deriveSecret(handshakeSecret, "c hs traffic", helloTranscriptHash)
		val serverHandshakeTrafficSecret = deriveSecret(handshakeSecret, "s hs traffic", helloTranscriptHash)

		earlySecret.fill(0)
		derivedSecret.fill(0)

		return HandshakeSecrets(
			handshakeSecret = handshakeSecret,
			clientHandshakeTrafficSecret = clientHandshakeTrafficSecret,
			serverHandshakeTrafficSecret = serverHandshakeTrafficSecret,
		)
	}

	fun computeApplicationSecrets(
		handshakeSecret: ByteArray,
		finishedTranscriptHash: ByteArray,
	): ApplicationSecrets {
		val emptyHash = hashEmpty()

		val derivedSecret = deriveSecret(handshakeSecret, "derived", emptyHash)
		val masterSecret = extract(derivedSecret, ByteArray(hashLength))

		val clientAppTrafficSecret = deriveSecret(masterSecret, "c ap traffic", finishedTranscriptHash)
		val serverAppTrafficSecret = deriveSecret(masterSecret, "s ap traffic", finishedTranscriptHash)

		derivedSecret.fill(0)
		masterSecret.fill(0)

		return ApplicationSecrets(
			clientAppTrafficSecret = clientAppTrafficSecret,
			serverAppTrafficSecret = serverAppTrafficSecret,
		)
	}

	private fun hashEmpty(): ByteArray {
		val digest = CryptographyProvider.Default.get(hashAlg)
		return digest.hasher().hashBlocking(ByteArray(0))
	}
}

internal class TrafficKeys(
	val key: ByteArray,
	val iv: ByteArray,
)

internal class HandshakeSecrets(
	val handshakeSecret: ByteArray,
	val clientHandshakeTrafficSecret: ByteArray,
	val serverHandshakeTrafficSecret: ByteArray,
)

internal class ApplicationSecrets(
	val clientAppTrafficSecret: ByteArray,
	val serverAppTrafficSecret: ByteArray,
)

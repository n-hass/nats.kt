@file:OptIn(dev.whyoleg.cryptography.DelicateCryptographyApi::class)

package io.natskt.tls.internal

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.ChaCha20Poly1305
import dev.whyoleg.cryptography.operations.IvAuthenticatedCipher

/**
 * TLS 1.3 record encryption/decryption.
 *
 * Differences from TLS 1.2:
 * - Nonce: XOR(iv, left-padded sequence number) -- no explicit nonce in record
 * - AAD: 5-byte outer record header (type=0x17, version=0x0303, length including tag+1)
 * - Plaintext has inner content type byte appended
 * - Outer record type is always ApplicationData (0x17)
 */
internal class Tls13Cipher private constructor(
	private val encryptCipher: IvAuthenticatedCipher,
	private val decryptCipher: IvAuthenticatedCipher,
	private val encryptIV: ByteArray,
	private val decryptIV: ByteArray,
	private val tagSize: Int,
) {
	private var encryptSeq: Long = 0L
	private var decryptSeq: Long = 0L

	// Separate buffers for encrypt/decrypt — these are called from different coroutines
	private val encryptNonce = ByteArray(12)
	private val decryptNonce = ByteArray(12)
	private val encryptAad = byteArrayOf(TlsRecordType.ApplicationData.code.toByte(), 0x03, 0x03, 0, 0)
	private val decryptAad = byteArrayOf(TlsRecordType.ApplicationData.code.toByte(), 0x03, 0x03, 0, 0)

	/**
	 * Encrypt plaintext for a TLS 1.3 record.
	 * Appends [innerType] byte to plaintext before encryption.
	 * Returns the encrypted payload (ciphertext + tag), ready for the record body.
	 */
	fun encrypt(
		plaintext: ByteArray,
		offset: Int,
		length: Int,
		innerType: TlsRecordType,
	): ByteArray {
		// Build plaintext + inner content type
		val input = ByteArray(length + 1)
		plaintext.copyInto(input, 0, offset, offset + length)
		input[length] = innerType.code.toByte()

		buildNonce(encryptIV, encryptSeq, encryptNonce)

		// AAD: type(0x17) || version(0x0303) || length (ciphertext + tag size)
		val encryptedLength = input.size + tagSize
		encryptAad[3] = (encryptedLength shr 8).toByte()
		encryptAad[4] = encryptedLength.toByte()

		val result = encryptCipher.encryptWithIvBlocking(encryptNonce, input, encryptAad)
		encryptSeq++
		return result
	}

	/**
	 * Decrypt a TLS 1.3 record payload.
	 * Returns the plaintext and the inner content type.
	 */
	fun decrypt(
		recordData: ByteArray,
		offset: Int,
		length: Int,
	): Tls13Plaintext {
		buildNonce(decryptIV, decryptSeq, decryptNonce)

		// AAD: outer record header
		decryptAad[3] = (length shr 8).toByte()
		decryptAad[4] = length.toByte()

		val ciphertext =
			if (offset == 0 && length == recordData.size) {
				recordData
			} else {
				recordData.copyOfRange(offset, offset + length)
			}

		val decrypted = decryptCipher.decryptWithIvBlocking(decryptNonce, ciphertext, decryptAad)
		decryptSeq++

		// Last byte is inner content type; strip trailing zeros per RFC 8446 5.4
		var end = decrypted.size - 1
		while (end >= 0 && decrypted[end] == 0.toByte()) end--
		if (end < 0) throw TlsException("TLS 1.3: empty inner plaintext")

		val innerType = TlsRecordType.byCode(decrypted[end].toInt() and 0xff)
		val plaintext = decrypted.copyOf(end)
		return Tls13Plaintext(innerType, plaintext)
	}

	companion object {
		fun createAesGcm(
			encryptKey: ByteArray,
			decryptKey: ByteArray,
			encryptIV: ByteArray,
			decryptIV: ByteArray,
		): Tls13Cipher {
			val aesGcm = CryptographyProvider.Default.get(AES.GCM)
			val eKey = aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, encryptKey)
			val dKey = aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, decryptKey)
			return Tls13Cipher(eKey.cipher(128.bits), dKey.cipher(128.bits), encryptIV, decryptIV, 16)
		}

		fun createChaCha20Poly1305(
			encryptKey: ByteArray,
			decryptKey: ByteArray,
			encryptIV: ByteArray,
			decryptIV: ByteArray,
		): Tls13Cipher {
			val chacha = CryptographyProvider.Default.get(ChaCha20Poly1305)
			val eKey = chacha.keyDecoder().decodeFromByteArrayBlocking(ChaCha20Poly1305.Key.Format.RAW, encryptKey)
			val dKey = chacha.keyDecoder().decodeFromByteArrayBlocking(ChaCha20Poly1305.Key.Format.RAW, decryptKey)
			return Tls13Cipher(eKey.cipher(), dKey.cipher(), encryptIV, decryptIV, 16)
		}

		private fun buildNonce(
			iv: ByteArray,
			seqNum: Long,
			out: ByteArray,
		) {
			// Left-pad sequence number to 12 bytes, then XOR with IV
			for (i in 0..3) {
				out[i] = iv[i]
			}
			for (i in 4..11) {
				val seqByte = (seqNum ushr ((11 - i) * 8)).toByte()
				out[i] = (iv[i].toInt() xor seqByte.toInt()).toByte()
			}
		}
	}
}

internal class Tls13Plaintext(
	val innerType: TlsRecordType,
	val data: ByteArray,
)

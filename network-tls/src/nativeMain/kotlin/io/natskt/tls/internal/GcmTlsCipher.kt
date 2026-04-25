@file:OptIn(dev.whyoleg.cryptography.DelicateCryptographyApi::class)

package io.natskt.tls.internal

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.operations.IvAuthenticatedCipher

internal class GcmTlsCipher(
	private val suite: SuiteInfo,
	keyMaterial: KeyMaterial,
) {
	private val clientCipher: IvAuthenticatedCipher
	private val serverCipher: IvAuthenticatedCipher

	// Pre-allocated and reused per record -- mutated in place
	private val encryptNonce = ByteArray(suite.ivLength)
	private val decryptNonce = ByteArray(suite.ivLength)
	private val encryptAad = ByteArray(13)
	private val decryptAad = ByteArray(13)

	private var outputCounter: Long = 0L
	private var inputCounter: Long = 0L

	init {
		val aesGcm = CryptographyProvider.Default.get(AES.GCM)
		val cKey = aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyMaterial.clientKey())
		val sKey = aesGcm.keyDecoder().decodeFromByteArrayBlocking(AES.Key.Format.RAW, keyMaterial.serverKey())
		clientCipher = cKey.cipher(suite.cipherTagBits.bits)
		serverCipher = sKey.cipher(suite.cipherTagBits.bits)

		// Copy fixed IV prefix once
		keyMaterial.clientIV().copyInto(encryptNonce)
		keyMaterial.serverIV().copyInto(decryptNonce)
	}

	/**
	 * Encrypts plaintext and returns the full record payload:
	 * explicitNonce(8) || ciphertext || tag
	 */
	fun encrypt(
		plaintext: ByteArray,
		offset: Int,
		length: Int,
		recordType: TlsRecordType,
	): ByteArray {
		val counter = outputCounter

		// Mutate nonce in place: fixed(4) || counter(8)
		longToBytes(counter, encryptNonce, suite.fixedIvLength)

		// Mutate AAD in place: seqNum(8) || type(1) || version(2) || length(2)
		longToBytes(counter, encryptAad, 0)
		encryptAad[8] = recordType.code.toByte()
		encryptAad[9] = 3
		encryptAad[10] = 3
		encryptAad[11] = (length shr 8).toByte()
		encryptAad[12] = length.toByte()

		// Single copy: crypto library returns new array
		val input =
			if (offset == 0 && length == plaintext.size) {
				plaintext
			} else {
				plaintext.copyOfRange(offset, offset + length)
			}
		val ciphertext = clientCipher.encryptWithIvBlocking(encryptNonce, input, encryptAad)

		outputCounter++

		// Build output: explicitNonce(8) || ciphertext+tag
		val output = ByteArray(8 + ciphertext.size)
		longToBytes(counter, output, 0)
		ciphertext.copyInto(output, 8)
		return output
	}

	/**
	 * Decrypts a record payload (explicitNonce(8) || ciphertext || tag) and returns plaintext.
	 */
	fun decrypt(
		recordData: ByteArray,
		offset: Int,
		length: Int,
		recordType: TlsRecordType,
	): ByteArray {
		val recordIv = bytesToLong(recordData, offset)

		// Mutate nonce in place
		longToBytes(recordIv, decryptNonce, suite.fixedIvLength)

		val ciphertextOffset = offset + 8
		val ciphertextLength = length - 8
		val contentSize = ciphertextLength - suite.cipherTagBytes

		// Mutate AAD in place
		longToBytes(inputCounter, decryptAad, 0)
		decryptAad[8] = recordType.code.toByte()
		decryptAad[9] = 3
		decryptAad[10] = 3
		decryptAad[11] = (contentSize shr 8).toByte()
		decryptAad[12] = contentSize.toByte()

		val ciphertext = recordData.copyOfRange(ciphertextOffset, ciphertextOffset + ciphertextLength)
		val plaintext = serverCipher.decryptWithIvBlocking(decryptNonce, ciphertext, decryptAad)

		inputCounter++
		return plaintext
	}
}

private fun bytesToLong(
	data: ByteArray,
	offset: Int,
): Long {
	var value = 0L
	for (i in 0..7) {
		value = (value shl 8) or (data[offset + i].toLong() and 0xffL)
	}
	return value
}

private fun longToBytes(
	value: Long,
	dst: ByteArray,
	offset: Int,
) {
	for (i in 0..7) {
		dst[offset + i] = (value ushr ((7 - i) * 8)).toByte()
	}
}

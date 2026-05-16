package io.natskt.tls.internal

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.HMAC

internal val MASTER_SECRET_LABEL = "master secret".encodeToByteArray()
internal val EXTENDED_MASTER_SECRET_LABEL = "extended master secret".encodeToByteArray()
internal val KEY_EXPANSION_LABEL = "key expansion".encodeToByteArray()
internal val CLIENT_FINISHED_LABEL = "client finished".encodeToByteArray()
internal val SERVER_FINISHED_LABEL = "server finished".encodeToByteArray()

internal fun prf(
	secret: ByteArray,
	label: ByteArray,
	seed: ByteArray,
	requiredLength: Int,
	hmacDigest: dev.whyoleg.cryptography.CryptographyAlgorithmId<Digest>,
): ByteArray {
	val hmac = CryptographyProvider.Default.get(HMAC)
	val key = hmac.keyDecoder(hmacDigest).decodeFromByteArrayBlocking(HMAC.Key.Format.RAW, secret)
	return pHash(key, label + seed, requiredLength)
}

private fun pHash(
	key: HMAC.Key,
	seed: ByteArray,
	requiredLength: Int,
): ByteArray {
	val generator = key.signatureGenerator()
	val result = ByteArray(requiredLength)
	var offset = 0

	var a = seed
	while (offset < requiredLength) {
		a = generator.generateSignatureBlocking(a)
		val chunk = generator.generateSignatureBlocking(a + seed)
		val toCopy = minOf(chunk.size, requiredLength - offset)
		chunk.copyInto(result, offset, 0, toCopy)
		offset += toCopy
	}

	return result
}

internal fun deriveKeyMaterial(
	masterSecret: ByteArray,
	seed: ByteArray,
	keySize: Int,
	macSize: Int,
	ivSize: Int,
	hmacDigest: dev.whyoleg.cryptography.CryptographyAlgorithmId<Digest>,
): ByteArray {
	val materialSize = 2 * macSize + 2 * keySize + 2 * ivSize
	return prf(masterSecret, KEY_EXPANSION_LABEL, seed, materialSize, hmacDigest)
}

internal fun deriveMasterSecret(
	preMasterSecret: ByteArray,
	clientRandom: ByteArray,
	serverRandom: ByteArray,
	hmacDigest: dev.whyoleg.cryptography.CryptographyAlgorithmId<Digest>,
): ByteArray = prf(preMasterSecret, MASTER_SECRET_LABEL, clientRandom + serverRandom, 48, hmacDigest)

internal fun deriveExtendedMasterSecret(
	preMasterSecret: ByteArray,
	sessionHash: ByteArray,
	hmacDigest: dev.whyoleg.cryptography.CryptographyAlgorithmId<Digest>,
): ByteArray = prf(preMasterSecret, EXTENDED_MASTER_SECRET_LABEL, sessionHash, 48, hmacDigest)

internal fun clientFinished(
	handshakeHash: ByteArray,
	masterSecret: ByteArray,
	hmacDigest: dev.whyoleg.cryptography.CryptographyAlgorithmId<Digest>,
): ByteArray = prf(masterSecret, CLIENT_FINISHED_LABEL, handshakeHash, 12, hmacDigest)

internal fun serverFinished(
	handshakeHash: ByteArray,
	masterSecret: ByteArray,
	length: Int = 12,
	hmacDigest: dev.whyoleg.cryptography.CryptographyAlgorithmId<Digest>,
): ByteArray = prf(masterSecret, SERVER_FINISHED_LABEL, handshakeHash, length, hmacDigest)

internal class KeyMaterial(
	private val material: ByteArray,
	private val keySize: Int,
	private val macSize: Int,
	private val fixedIvLength: Int,
) {
	fun clientKey(): ByteArray = material.copyOfRange(2 * macSize, 2 * macSize + keySize)

	fun serverKey(): ByteArray = material.copyOfRange(2 * macSize + keySize, 2 * macSize + 2 * keySize)

	fun clientIV(): ByteArray = material.copyOfRange(2 * macSize + 2 * keySize, 2 * macSize + 2 * keySize + fixedIvLength)

	fun serverIV(): ByteArray = material.copyOfRange(2 * macSize + 2 * keySize + fixedIvLength, 2 * macSize + 2 * keySize + 2 * fixedIvLength)
}

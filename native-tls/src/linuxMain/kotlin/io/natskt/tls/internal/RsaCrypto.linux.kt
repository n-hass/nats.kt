@file:OptIn(dev.whyoleg.cryptography.DelicateCryptographyApi::class)

package io.natskt.tls.internal

import dev.whyoleg.cryptography.CryptographyAlgorithmId
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA384
import dev.whyoleg.cryptography.algorithms.SHA512
import io.ktor.network.tls.TlsException

private fun RsaHash.toDigest(): CryptographyAlgorithmId<Digest> =
	when (this) {
		RsaHash.Sha256 -> SHA256
		RsaHash.Sha384 -> SHA384
		RsaHash.Sha512 -> SHA512
	}

private fun rsaPublicKeyFromCert(certDer: ByteArray): CertPublicKey.Rsa =
	extractPublicKeyFromCertificate(certDer) as? CertPublicKey.Rsa
		?: throw TlsException("Expected RSA public key in certificate")

internal actual fun verifyRsaPssSignature(
	leafCertDer: ByteArray,
	hash: RsaHash,
	signedData: ByteArray,
	signature: ByteArray,
): Boolean {
	val rsa = rsaPublicKeyFromCert(leafCertDer)
	val pk =
		CryptographyProvider.Default
			.get(RSA.PSS)
			.publicKeyDecoder(hash.toDigest())
			.decodeFromByteArrayBlocking(RSA.PublicKey.Format.DER, rsa.spkiDer)
	return pk.signatureVerifier().tryVerifySignatureBlocking(signedData, signature)
}

internal actual fun verifyRsaPkcs1Signature(
	leafCertDer: ByteArray,
	hash: RsaHash,
	signedData: ByteArray,
	signature: ByteArray,
): Boolean {
	val rsa = rsaPublicKeyFromCert(leafCertDer)
	val pk =
		CryptographyProvider.Default
			.get(RSA.PKCS1)
			.publicKeyDecoder(hash.toDigest())
			.decodeFromByteArrayBlocking(RSA.PublicKey.Format.DER, rsa.spkiDer)
	return pk.signatureVerifier().tryVerifySignatureBlocking(signedData, signature)
}

internal actual fun encryptRsaPkcs1(
	leafCertDer: ByteArray,
	plaintext: ByteArray,
): ByteArray {
	val rsa = rsaPublicKeyFromCert(leafCertDer)
	// RSAES-PKCS1-v1_5 encryption is digest-independent; the API requires a digest parameter
	// only because the same provider services signing too.
	val pk =
		CryptographyProvider.Default
			.get(RSA.PKCS1)
			.publicKeyDecoder(SHA256)
			.decodeFromByteArrayBlocking(RSA.PublicKey.Format.DER, rsa.spkiDer)
	return pk.encryptor().encryptBlocking(plaintext)
}

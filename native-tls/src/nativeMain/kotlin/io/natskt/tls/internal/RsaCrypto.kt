package io.natskt.tls.internal

/**
 * Hash function used in an RSA signature scheme.
 */
internal enum class RsaHash {
	Sha256,
	Sha384,
	Sha512,
}

/**
 * Verify an RSA-PSS signature against the public key embedded in [leafCertDer].
 *
 * Used by TLS 1.3 CertificateVerify and TLS 1.2 ServerKeyExchange when the
 * server's signature scheme uses RSASSA-PSS.
 */
internal expect fun verifyRsaPssSignature(
	leafCertDer: ByteArray,
	hash: RsaHash,
	signedData: ByteArray,
	signature: ByteArray,
): Boolean

/**
 * Verify an RSA-PKCS#1-v1_5 signature against the public key embedded in [leafCertDer].
 *
 * Used by TLS 1.2 ServerKeyExchange when the server's signature scheme is
 * rsa_pkcs1_*.
 */
internal expect fun verifyRsaPkcs1Signature(
	leafCertDer: ByteArray,
	hash: RsaHash,
	signedData: ByteArray,
	signature: ByteArray,
): Boolean

/**
 * Encrypt [plaintext] with RSAES-PKCS#1-v1_5 using the public key embedded in [leafCertDer].
 *
 * Used by the TLS 1.2 static-RSA key exchange (TLS_RSA_WITH_*) to wrap the premaster secret.
 */
internal expect fun encryptRsaPkcs1(
	leafCertDer: ByteArray,
	plaintext: ByteArray,
): ByteArray

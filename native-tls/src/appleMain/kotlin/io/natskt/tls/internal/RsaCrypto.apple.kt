@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.natskt.tls.internal

import io.ktor.network.tls.TlsException
import platform.Security.SecKeyAlgorithm
import platform.Security.kSecKeyAlgorithmRSAEncryptionPKCS1
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA384
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA512
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePSSSHA256
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePSSSHA384
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePSSSHA512

private fun pssAlgorithm(hash: RsaHash): SecKeyAlgorithm =
	when (hash) {
		RsaHash.Sha256 -> kSecKeyAlgorithmRSASignatureMessagePSSSHA256
		RsaHash.Sha384 -> kSecKeyAlgorithmRSASignatureMessagePSSSHA384
		RsaHash.Sha512 -> kSecKeyAlgorithmRSASignatureMessagePSSSHA512
	} ?: throw TlsException("RSA-PSS-$hash not available")

private fun pkcs1Algorithm(hash: RsaHash): SecKeyAlgorithm =
	when (hash) {
		RsaHash.Sha256 -> kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
		RsaHash.Sha384 -> kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA384
		RsaHash.Sha512 -> kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA512
	} ?: throw TlsException("RSA-PKCS#1-$hash not available")

internal actual fun verifyRsaPssSignature(
	leafCertDer: ByteArray,
	hash: RsaHash,
	signedData: ByteArray,
	signature: ByteArray,
): Boolean = verifySignatureWithCert(leafCertDer, pssAlgorithm(hash), signedData, signature)

internal actual fun verifyRsaPkcs1Signature(
	leafCertDer: ByteArray,
	hash: RsaHash,
	signedData: ByteArray,
	signature: ByteArray,
): Boolean = verifySignatureWithCert(leafCertDer, pkcs1Algorithm(hash), signedData, signature)

internal actual fun encryptRsaPkcs1(
	leafCertDer: ByteArray,
	plaintext: ByteArray,
): ByteArray {
	val algorithm =
		kSecKeyAlgorithmRSAEncryptionPKCS1 ?: throw TlsException("RSA-PKCS#1 encryption not available")
	return encryptWithCert(leafCertDer, algorithm, plaintext)
}

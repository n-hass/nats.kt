@file:OptIn(
	kotlinx.cinterop.ExperimentalForeignApi::class,
	kotlinx.cinterop.BetaInteropApi::class,
)

package io.natskt.tls.internal

import io.ktor.network.tls.TlsException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFErrorCopyDescription
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSString
import platform.Security.SecCertificateCopyKey
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecKeyAlgorithm
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyRef
import platform.Security.SecKeyVerifySignature
import platform.posix.uint8_tVar

/**
 * Wrap [this] as a CFData and pass it to [block].
 *
 * The pin only spans the [CFDataCreate] call: CFDataCreate copies the bytes into the
 * CFDataRef, so we intentionally `unpin` as soon as it returns — before [block] runs.
 * After [block] returns, `CFRelease` frees the CFDataRef.
 */
internal inline fun <R> ByteArray.asCFData(block: (CFDataRef) -> R): R {
	val pinned = pin()
	val cfData =
		try {
			CFDataCreate(
				kCFAllocatorDefault,
				pinned.addressOf(0).reinterpret<uint8_tVar>(),
				size.toLong(),
			) ?: throw TlsException("Failed to create CFData")
		} finally {
			pinned.unpin()
		}
	try {
		return block(cfData)
	} finally {
		CFRelease(cfData)
	}
}

internal fun derToSecCertificate(certDer: ByteArray): SecCertificateRef =
	certDer.asCFData { cfData ->
		SecCertificateCreateWithData(kCFAllocatorDefault, cfData)
			?: throw TlsException("Invalid X.509 certificate")
	}

internal fun CFErrorRefVar.describe(): String =
	value?.let { err ->
		val descRef = CFErrorCopyDescription(err)
		val desc = (CFBridgingRelease(descRef) as? NSString)?.toString() ?: "unknown"
		CFRelease(err)
		desc
	} ?: "unknown"

/**
 * Verify a signature using the public key extracted from [leafCertDer].
 */
internal fun verifySignatureWithCert(
	leafCertDer: ByteArray,
	algorithm: SecKeyAlgorithm,
	signedData: ByteArray,
	signature: ByteArray,
): Boolean =
	withCertPublicKey(leafCertDer) { key ->
		memScoped {
			val errorVar = alloc<CFErrorRefVar>()
			signedData.asCFData { dataCf ->
				signature.asCFData { sigCf ->
					SecKeyVerifySignature(key, algorithm, dataCf, sigCf, errorVar.ptr)
				}
			}
		}
	}

/**
 * Encrypt [plaintext] with [algorithm] using the public key extracted from [leafCertDer].
 */
internal fun encryptWithCert(
	leafCertDer: ByteArray,
	algorithm: SecKeyAlgorithm,
	plaintext: ByteArray,
): ByteArray =
	withCertPublicKey(leafCertDer) { key ->
		memScoped {
			val errorVar = alloc<CFErrorRefVar>()
			plaintext.asCFData { plainCf ->
				val outCf =
					SecKeyCreateEncryptedData(key, algorithm, plainCf, errorVar.ptr)
						?: throw TlsException("RSA encryption failed: ${errorVar.describe()}")
				try {
					cfDataToByteArray(outCf)
				} finally {
					CFRelease(outCf)
				}
			}
		}
	}

private inline fun <R> withCertPublicKey(
	leafCertDer: ByteArray,
	block: (SecKeyRef) -> R,
): R {
	val cert = derToSecCertificate(leafCertDer)
	try {
		val key = SecCertificateCopyKey(cert) ?: throw TlsException("Cannot extract public key from certificate")
		try {
			return block(key)
		} finally {
			CFRelease(key)
		}
	} finally {
		CFRelease(cert)
	}
}

@OptIn(kotlinx.cinterop.UnsafeNumber::class)
private fun cfDataToByteArray(data: CFDataRef): ByteArray {
	val length = platform.CoreFoundation.CFDataGetLength(data)
	val bytePtr =
		platform.CoreFoundation.CFDataGetBytePtr(data)
			?: throw TlsException("CFDataGetBytePtr returned null")
	return ByteArray(length.toInt()) { i -> bytePtr[i].toByte() }
}

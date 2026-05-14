@file:OptIn(
	kotlinx.cinterop.ExperimentalForeignApi::class,
	kotlinx.cinterop.BetaInteropApi::class,
)

package io.natskt.tls.cert

import io.ktor.network.tls.TlsException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayAppendValue
import platform.CoreFoundation.CFArrayCreateMutable
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFErrorCopyDescription
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSString
import platform.Security.SecCertificateCopyKey
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecKeyAlgorithm
import platform.Security.SecKeyRef
import platform.Security.SecKeyVerifySignature
import platform.Security.SecPolicyCreateSSL
import platform.Security.SecPolicyRef
import platform.Security.SecTrustCreateWithCertificates
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustRefVar
import platform.Security.errSecSuccess
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA384
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA512
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA384
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA512
import platform.posix.uint8_tVar
import kotlin.time.Clock

internal actual fun validateCertificateChain(
	certs: List<ByteArray>,
	hostname: String?,
	trustAnchorsDer: List<ByteArray>,
) {
	if (certs.isEmpty()) throw TlsException("No certificates to validate")

	if (trustAnchorsDer.isEmpty()) {
		// System trust: SecTrust + SSL policy works correctly using built-in roots.
		validateAgainstSystemTrust(certs, hostname)
	} else {
		// SecTrust's policy machinery on iOS Simulator refuses to chain leaf → anchor via
		// SecTrustSetAnchorCertificates (returns errSecPolicyDenied even when the signature
		// is valid), so for custom anchors we walk the chain ourselves with SecKey.
		validateAgainstCustomAnchors(certs, hostname, trustAnchorsDer)
	}
}

private fun validateAgainstCustomAnchors(
	certs: List<ByteArray>,
	hostname: String?,
	trustAnchorsDer: List<ByteArray>,
) {
	val chain = certs.map { parseCertInfo(it) }
	val anchors = trustAnchorsDer.map { parseCertInfo(it) }
	val nowMillis = Clock.System.now().toEpochMilliseconds()

	val intermediates = chain.drop(1).toMutableList()
	var current = chain[0]

	while (true) {
		checkValidity(current, nowMillis)

		val anchor = anchors.firstOrNull { it.subjectDer.contentEquals(current.issuerDer) }
		if (anchor != null) {
			verifyCertSignature(current, anchor.der)
			checkValidity(anchor, nowMillis)
			break
		}

		val nextIdx = intermediates.indexOfFirst { it.subjectDer.contentEquals(current.issuerDer) }
		if (nextIdx < 0) throw TlsException("Certificate chain validation failed: no trusted issuer")
		val next = intermediates.removeAt(nextIdx)
		verifyCertSignature(current, next.der)
		current = next
	}

	if (hostname != null) verifyHostname(hostname, chain[0].sans)
}

private fun checkValidity(
	info: CertInfo,
	nowMillis: Long,
) {
	if (nowMillis < info.notBeforeMillis) throw TlsException("Certificate not yet valid")
	if (nowMillis > info.notAfterMillis) throw TlsException("Certificate expired")
}

private fun verifyCertSignature(
	child: CertInfo,
	issuerDer: ByteArray,
) {
	val algorithm =
		child.signatureAlgorithm?.toSecKeyAlgorithm()
			?: throw TlsException("Unsupported certificate signature algorithm: ${child.signatureAlgOid}")

	memScoped {
		val issuerSecCert = derToSecCertificate(issuerDer)
		try {
			val key: SecKeyRef =
				SecCertificateCopyKey(issuerSecCert)
					?: throw TlsException("Unable to extract issuer public key")
			try {
				child.tbsBytes.asCFData { tbsCfData ->
					child.signatureBytes.asCFData { sigCfData ->
						val errorVar = alloc<CFErrorRefVar>()
						val ok = SecKeyVerifySignature(key, algorithm, tbsCfData, sigCfData, errorVar.ptr)
						if (!ok) throw TlsException("Certificate signature verification failed: ${errorVar.describe()}")
					}
				}
			} finally {
				CFRelease(key)
			}
		} finally {
			CFRelease(issuerSecCert)
		}
	}
}

private fun SignatureAlgorithm.toSecKeyAlgorithm(): SecKeyAlgorithm =
	when (this) {
		SignatureAlgorithm.EcdsaSha256 -> kSecKeyAlgorithmECDSASignatureMessageX962SHA256
		SignatureAlgorithm.EcdsaSha384 -> kSecKeyAlgorithmECDSASignatureMessageX962SHA384
		SignatureAlgorithm.EcdsaSha512 -> kSecKeyAlgorithmECDSASignatureMessageX962SHA512
		SignatureAlgorithm.RsaSha256 -> kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
		SignatureAlgorithm.RsaSha384 -> kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA384
		SignatureAlgorithm.RsaSha512 -> kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA512
	} ?: throw TlsException("Security framework returned null for SecKeyAlgorithm $name")

private fun validateAgainstSystemTrust(
	certs: List<ByteArray>,
	hostname: String?,
) {
	memScoped {
		val certArray =
			CFArrayCreateMutable(kCFAllocatorDefault, certs.size.toLong(), null)
				?: throw TlsException("Failed to create mutable array")
		val secCerts = mutableListOf<SecCertificateRef>()
		val cfHostname: CFStringRef? =
			hostname?.let {
				@Suppress("UNCHECKED_CAST")
				CFBridgingRetain(it) as CFStringRef?
			}

		try {
			for (certDer in certs) {
				val secCert = derToSecCertificate(certDer)
				secCerts.add(secCert)
				CFArrayAppendValue(certArray, secCert)
			}

			val policy: SecPolicyRef =
				SecPolicyCreateSSL(true, cfHostname) ?: throw TlsException("Failed to create SSL policy")

			val trustRef = alloc<SecTrustRefVar>()
			val status = SecTrustCreateWithCertificates(certArray, policy, trustRef.ptr)
			CFRelease(policy)
			if (status != errSecSuccess) throw TlsException("SecTrustCreate failed: $status")

			val trust = trustRef.value ?: throw TlsException("SecTrust is null")
			val errorVar = alloc<CFErrorRefVar>()
			val trusted = SecTrustEvaluateWithError(trust, errorVar.ptr)
			CFRelease(trust)
			if (!trusted) {
				throw TlsException("Certificate chain validation failed (untrusted by system): ${errorVar.describe()}")
			}
		} finally {
			secCerts.forEach { CFRelease(it) }
			CFRelease(certArray)
			if (cfHostname != null) CFRelease(cfHostname)
		}
	}
}

private fun CFErrorRefVar.describe(): String =
	value?.let { err ->
		val descRef = CFErrorCopyDescription(err)
		val desc = (CFBridgingRelease(descRef) as? NSString)?.toString() ?: "unknown"
		CFRelease(err)
		desc
	} ?: "unknown"

private inline fun <R> ByteArray.asCFData(block: (platform.CoreFoundation.CFDataRef) -> R): R {
	val pinned = pin()
	val cfData =
		CFDataCreate(
			kCFAllocatorDefault,
			pinned.addressOf(0).reinterpret<uint8_tVar>(),
			size.toLong(),
		) ?: run {
			pinned.unpin()
			throw TlsException("Failed to create CFData")
		}
	pinned.unpin()
	try {
		return block(cfData)
	} finally {
		CFRelease(cfData)
	}
}

private fun derToSecCertificate(certDer: ByteArray): SecCertificateRef {
	certDer.asCFData { cfData ->
		return SecCertificateCreateWithData(kCFAllocatorDefault, cfData)
			?: throw TlsException("Invalid X.509 certificate")
	}
}

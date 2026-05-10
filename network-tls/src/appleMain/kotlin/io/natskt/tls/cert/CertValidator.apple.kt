@file:OptIn(
	kotlinx.cinterop.ExperimentalForeignApi::class,
	kotlinx.cinterop.BetaInteropApi::class,
)

package io.natskt.tls.cert

import io.natskt.tls.internal.TlsException
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
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.CFBridgingRetain
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.Security.SecPolicyCreateSSL
import platform.Security.SecTrustCreateWithCertificates
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustRefVar
import platform.Security.SecTrustSetAnchorCertificates
import platform.Security.SecTrustSetAnchorCertificatesOnly
import platform.Security.errSecSuccess
import platform.posix.uint8_tVar

internal actual fun validateCertificateChain(
	certs: List<ByteArray>,
	hostname: String?,
	trustAnchorsDer: List<ByteArray>,
) {
	if (certs.isEmpty()) throw TlsException("No certificates to validate")

	memScoped {
		val certArray =
			CFArrayCreateMutable(kCFAllocatorDefault, certs.size.toLong(), null)
				?: throw TlsException("Failed to create mutable array")
		val secCerts = mutableListOf<SecCertificateRef>()

		val anchorArray =
			if (trustAnchorsDer.isEmpty()) {
				null
			} else {
				CFArrayCreateMutable(kCFAllocatorDefault, trustAnchorsDer.size.toLong(), null)
					?: throw TlsException("Failed to create anchor array")
			}
		val anchorCerts = mutableListOf<SecCertificateRef>()

		try {
			for (certDer in certs) {
				val secCert = derToSecCertificate(certDer)
				secCerts.add(secCert)
				CFArrayAppendValue(certArray, secCert)
			}

			if (anchorArray != null) {
				for (anchorDer in trustAnchorsDer) {
					val secCert = derToSecCertificate(anchorDer)
					anchorCerts.add(secCert)
					CFArrayAppendValue(anchorArray, secCert)
				}
			}

			val cfHostname: CFStringRef? =
				hostname?.let {
					@Suppress("UNCHECKED_CAST")
					CFBridgingRetain(it) as CFStringRef?
				}

			val policy = SecPolicyCreateSSL(true, cfHostname)
			if (cfHostname != null) CFRelease(cfHostname)
			if (policy == null) throw TlsException("Failed to create SSL policy")

			val trustRef = alloc<SecTrustRefVar>()
			val status = SecTrustCreateWithCertificates(certArray, policy, trustRef.ptr)
			CFRelease(policy)

			if (status != errSecSuccess) throw TlsException("SecTrustCreate failed: $status")

			val trust = trustRef.value ?: throw TlsException("SecTrust is null")

			if (anchorArray != null) {
				val anchorStatus = SecTrustSetAnchorCertificates(trust, anchorArray)
				if (anchorStatus != errSecSuccess) {
					CFRelease(trust)
					throw TlsException("SecTrustSetAnchorCertificates failed: $anchorStatus")
				}
				val onlyStatus = SecTrustSetAnchorCertificatesOnly(trust, true)
				if (onlyStatus != errSecSuccess) {
					CFRelease(trust)
					throw TlsException("SecTrustSetAnchorCertificatesOnly failed: $onlyStatus")
				}
			}

			val trusted = SecTrustEvaluateWithError(trust, null)
			CFRelease(trust)

			if (!trusted) {
				throw TlsException("Certificate chain validation failed (untrusted by system)")
			}
		} finally {
			secCerts.forEach { CFRelease(it) }
			anchorCerts.forEach { CFRelease(it) }
			CFRelease(certArray)
			anchorArray?.let { CFRelease(it) }
		}
	}
}

private fun derToSecCertificate(certDer: ByteArray): SecCertificateRef {
	val pinned = certDer.pin()
	val cfData =
		CFDataCreate(
			kCFAllocatorDefault,
			pinned.addressOf(0).reinterpret<uint8_tVar>(),
			certDer.size.toLong(),
		)
	pinned.unpin()

	if (cfData == null) throw TlsException("Failed to create CFData")
	val secCert = SecCertificateCreateWithData(kCFAllocatorDefault, cfData)
	CFRelease(cfData)
	return secCert ?: throw TlsException("Invalid X.509 certificate")
}

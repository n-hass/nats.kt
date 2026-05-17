@file:OptIn(
	kotlinx.cinterop.ExperimentalForeignApi::class,
	kotlinx.cinterop.BetaInteropApi::class,
)

package io.natskt.tls.cert

import io.ktor.network.tls.TlsException
import io.natskt.tls.internal.derToSecCertificate
import io.natskt.tls.internal.describe
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayAppendValue
import platform.CoreFoundation.CFArrayCreateMutable
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Foundation.CFBridgingRetain
import platform.Security.SecCertificateRef
import platform.Security.SecPolicyCreateSSL
import platform.Security.SecPolicyRef
import platform.Security.SecTrustCreateWithCertificates
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustRefVar
import platform.Security.SecTrustSetAnchorCertificates
import platform.Security.SecTrustSetAnchorCertificatesOnly
import platform.Security.errSecSuccess

internal actual fun validateCertificateChain(
	certs: List<ByteArray>,
	hostname: String?,
	trustAnchorsDer: List<ByteArray>,
) {
	if (certs.isEmpty()) throw TlsException("No certificates to validate")

	memScoped {
		val secCerts = mutableListOf<SecCertificateRef>()
		val secAnchors = mutableListOf<SecCertificateRef>()
		val cfHostname: CFStringRef? =
			hostname?.let {
				@Suppress("UNCHECKED_CAST")
				CFBridgingRetain(it) as CFStringRef?
			}

		val certArray =
			CFArrayCreateMutable(kCFAllocatorDefault, certs.size.toLong(), null)
				?: throw TlsException("Failed to create certificate array")

		val anchorArray =
			if (trustAnchorsDer.isNotEmpty()) {
				CFArrayCreateMutable(kCFAllocatorDefault, trustAnchorsDer.size.toLong(), null)
					?: throw TlsException("Failed to create anchor array")
			} else {
				null
			}

		try {
			for (certDer in certs) {
				val secCert = derToSecCertificate(certDer)
				secCerts.add(secCert)
				CFArrayAppendValue(certArray, secCert)
			}
			if (anchorArray != null) {
				for (anchorDer in trustAnchorsDer) {
					val secAnchor = derToSecCertificate(anchorDer)
					secAnchors.add(secAnchor)
					CFArrayAppendValue(anchorArray, secAnchor)
				}
			}

			val policy: SecPolicyRef =
				SecPolicyCreateSSL(true, cfHostname) ?: throw TlsException("Failed to create SSL policy")

			val trustRef = alloc<SecTrustRefVar>()
			val status = SecTrustCreateWithCertificates(certArray, policy, trustRef.ptr)
			CFRelease(policy)
			if (status != errSecSuccess) throw TlsException("SecTrustCreate failed: $status")

			val trust = trustRef.value ?: throw TlsException("SecTrust is null")
			try {
				if (anchorArray != null) {
					val setStatus = SecTrustSetAnchorCertificates(trust, anchorArray)
					if (setStatus != errSecSuccess) {
						throw TlsException("SecTrustSetAnchorCertificates failed: $setStatus")
					}
					// Restrict trust to the caller's anchors; do not fall back to the system trust store.
					val onlyStatus = SecTrustSetAnchorCertificatesOnly(trust, true)
					if (onlyStatus != errSecSuccess) {
						throw TlsException("SecTrustSetAnchorCertificatesOnly failed: $onlyStatus")
					}
				}

				val errorVar = alloc<CFErrorRefVar>()
				val trusted = SecTrustEvaluateWithError(trust, errorVar.ptr)
				if (!trusted) {
					throw TlsException("Certificate chain validation failed: ${errorVar.describe()}")
				}
			} finally {
				CFRelease(trust)
			}
		} finally {
			secCerts.forEach { CFRelease(it) }
			secAnchors.forEach { CFRelease(it) }
			CFRelease(certArray)
			if (anchorArray != null) CFRelease(anchorArray)
			if (cfHostname != null) CFRelease(cfHostname)
		}
	}
}

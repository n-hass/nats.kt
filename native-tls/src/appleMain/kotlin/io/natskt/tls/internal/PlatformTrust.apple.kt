@file:OptIn(
	ExperimentalForeignApi::class,
	BetaInteropApi::class,
)

package io.natskt.tls.internal

import io.natskt.tls.NativeTlsConfigBuilder
import io.natskt.tls.openssl.OPENSSL_STACK
import io.natskt.tls.openssl.OPENSSL_sk_num
import io.natskt.tls.openssl.OPENSSL_sk_value
import io.natskt.tls.openssl.SSL_CTX
import io.natskt.tls.openssl.SSL_CTX_set_cert_verify_callback
import io.natskt.tls.openssl.X509
import io.natskt.tls.openssl.X509_STORE_CTX
import io.natskt.tls.openssl.X509_STORE_CTX_get0_cert
import io.natskt.tls.openssl.X509_STORE_CTX_get0_untrusted
import io.natskt.tls.openssl.i2d_X509
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayAppendValue
import platform.CoreFoundation.CFArrayCreateMutable
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecCertificateRef
import platform.Security.SecPolicyCreateSSL
import platform.Security.SecPolicyRef
import platform.Security.SecTrustCreateWithCertificates
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustRef
import platform.Security.SecTrustRefVar
import platform.Security.SecTrustSetAnchorCertificates
import platform.Security.SecTrustSetAnchorCertificatesOnly
import platform.Security.errSecSuccess
import platform.posix.uint8_tVar

internal actual fun configurePlatformTrust(
	ctx: CPointer<SSL_CTX>,
	config: NativeTlsConfigBuilder,
): () -> Unit {
	if (!config.verifyCertificates) return {}
	val ref = StableRef.create(TrustContext(config.trustAnchorsDer, config.serverName))
	SSL_CTX_set_cert_verify_callback(ctx, secTrustVerifyCallback, ref.asCPointer())
	return { ref.dispose() }
}

private class TrustContext(
	val anchorsDer: List<ByteArray>,
	val serverName: String?,
)

private val secTrustVerifyCallback:
	CPointer<CFunction<(CPointer<X509_STORE_CTX>?, COpaquePointer?) -> Int>> =
	staticCFunction(::secTrustVerify)

private fun secTrustVerify(
	storeCtx: CPointer<X509_STORE_CTX>?,
	arg: COpaquePointer?,
): Int {
	if (storeCtx == null || arg == null) return 0
	// A Kotlin exception escaping into the OpenSSL C frame is undefined behavior; trap and reject.
	return try {
		val tctx = arg.asStableRef<TrustContext>().get()
		evaluateWithSecTrust(storeCtx, tctx.anchorsDer, tctx.serverName)
	} catch (_: Throwable) {
		0
	}
}

private fun evaluateWithSecTrust(
	storeCtx: CPointer<X509_STORE_CTX>,
	anchorsDer: List<ByteArray>,
	serverName: String?,
): Int {
	val leaf = X509_STORE_CTX_get0_cert(storeCtx) ?: return 0
	val untrusted = X509_STORE_CTX_get0_untrusted(storeCtx)

	val secCerts = mutableListOf<SecCertificateRef>()
	val certArray = CFArrayCreateMutable(kCFAllocatorDefault, 0, null) ?: return 0
	try {
		val leafSec = x509ToSecCertificate(leaf) ?: return 0
		secCerts += leafSec
		CFArrayAppendValue(certArray, leafSec)

		if (untrusted != null) {
			// X509_STORE_CTX_get0_untrusted returns the typed STACK_OF(X509) — reinterpret to the
			// generic OPENSSL_STACK that OPENSSL_sk_num / OPENSSL_sk_value operate on.
			val stack = untrusted.reinterpret<OPENSSL_STACK>()
			val n = OPENSSL_sk_num(stack)
			for (i in 0 until n) {
				val raw = OPENSSL_sk_value(stack, i) ?: continue
				val secCert = x509ToSecCertificate(raw.reinterpret<X509>()) ?: continue
				secCerts += secCert
				CFArrayAppendValue(certArray, secCert)
			}
		}

		val policy = createSslPolicy(serverName) ?: return 0
		try {
			return memScoped {
				val trustOut = alloc<SecTrustRefVar>()
				val status = SecTrustCreateWithCertificates(certArray, policy, trustOut.ptr)
				if (status != errSecSuccess) return@memScoped 0
				val trust = trustOut.value ?: return@memScoped 0
				try {
					if (anchorsDer.isNotEmpty() && !applyAnchors(trust, anchorsDer)) {
						return@memScoped 0
					}
					val errVar = alloc<CFErrorRefVar>()
					val ok = SecTrustEvaluateWithError(trust, errVar.ptr)
					errVar.value?.let { CFRelease(it) }
					if (ok) 1 else 0
				} finally {
					CFRelease(trust)
				}
			}
		} finally {
			CFRelease(policy)
		}
	} finally {
		secCerts.forEach { CFRelease(it) }
		CFRelease(certArray)
	}
}

private fun createSslPolicy(serverName: String?): SecPolicyRef? {
	if (serverName == null) return SecPolicyCreateSSL(true, null)
	val cfHostname =
		CFStringCreateWithCString(
			kCFAllocatorDefault,
			serverName,
			kCFStringEncodingUTF8,
		) ?: return null
	return try {
		SecPolicyCreateSSL(true, cfHostname)
	} finally {
		CFRelease(cfHostname)
	}
}

private fun applyAnchors(
	trust: SecTrustRef,
	anchorsDer: List<ByteArray>,
): Boolean {
	val anchorsArray = CFArrayCreateMutable(kCFAllocatorDefault, 0, null) ?: return false
	val secAnchors = mutableListOf<SecCertificateRef>()
	try {
		for (der in anchorsDer) {
			val anchorSec =
				runCatching { derToSecCertificate(der) }.getOrNull() ?: return false
			secAnchors += anchorSec
			CFArrayAppendValue(anchorsArray, anchorSec)
		}
		if (SecTrustSetAnchorCertificates(trust, anchorsArray) != errSecSuccess) return false
		// Caller-supplied anchors only — don't fall back to the system trust store.
		if (SecTrustSetAnchorCertificatesOnly(trust, true) != errSecSuccess) return false
		return true
	} finally {
		secAnchors.forEach { CFRelease(it) }
		CFRelease(anchorsArray)
	}
}

private fun x509ToSecCertificate(x509: CPointer<X509>): SecCertificateRef? {
	// i2d_X509 with NULL out returns the DER length.
	val len = i2d_X509(x509, null)
	if (len <= 0) return null
	val buf = ByteArray(len)
	return memScoped {
		val pinned = buf.pin()
		try {
			val ptrVar = alloc<CPointerVar<uint8_tVar>>()
			ptrVar.value = pinned.addressOf(0).reinterpret()
			val written = i2d_X509(x509, ptrVar.ptr.reinterpret())
			if (written <= 0) null else derToSecCertificate(buf)
		} finally {
			pinned.unpin()
		}
	}
}

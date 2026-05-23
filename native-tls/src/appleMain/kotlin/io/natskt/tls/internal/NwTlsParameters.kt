@file:OptIn(
	kotlinx.cinterop.ExperimentalForeignApi::class,
	kotlinx.cinterop.BetaInteropApi::class,
)

package io.natskt.tls.internal

import io.ktor.network.tls.TlsException
import io.natskt.tls.NativeTlsConfigBuilder
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFArrayAppendValue
import platform.CoreFoundation.CFArrayCreateMutable
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Network._nw_parameters_configure_protocol_default_configuration
import platform.Network.nw_parameters_create_secure_tcp
import platform.Network.nw_parameters_t
import platform.Network.nw_protocol_options_t
import platform.Network.nw_tls_copy_sec_protocol_options
import platform.Security.SecCertificateRef
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustSetAnchorCertificates
import platform.Security.SecTrustSetAnchorCertificatesOnly
import platform.Security.errSecSuccess
import platform.Security.sec_protocol_options_set_min_tls_protocol_version
import platform.Security.sec_protocol_options_set_tls_server_name
import platform.Security.sec_protocol_options_set_verify_block
import platform.Security.sec_trust_copy_ref
import platform.Security.tls_protocol_version_TLSv12
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create

// Network.framework's verify_block signature in K/N maps `sec_protocol_metadata_t` and
// `sec_trust_t` to `NSObject?`, and `sec_protocol_verify_complete_t` to `(Boolean) -> Unit`.
// The whole parameter is nullable, so the lambda must be typed accordingly.
private typealias VerifyBlock = ((NSObject?, NSObject?, ((Boolean) -> Unit)?) -> Unit)?

/**
 * Builds an `nw_parameters_t` for a client TLS connection. The returned parameters object is
 * retained — caller must hand it to `nw_connection_create`, which retains its own reference.
 *
 * TLS policy:
 * - Server name (SNI + Subject Alternative Name match) from [NativeTlsConfigBuilder.serverName].
 * - Minimum protocol version pinned to TLS 1.2.
 * - When [NativeTlsConfigBuilder.trustAnchorsDer] is non-empty the chain is evaluated against
 *   that list only (system roots are NOT additionally trusted); when empty the platform default
 *   trust store is used.
 * - When [NativeTlsConfigBuilder.verifyCertificates] is `false` a verify block accepts every
 *   chain; intended for tests.
 */
internal fun buildSecureTcpParameters(config: NativeTlsConfigBuilder): nw_parameters_t {
	val configureTls: (nw_protocol_options_t) -> Unit = configure@{ tlsOptions ->
		if (tlsOptions == null) return@configure
		val secOpts =
			nw_tls_copy_sec_protocol_options(tlsOptions)
				?: throw TlsException("nw_tls_copy_sec_protocol_options returned null")

		config.serverName?.let { sec_protocol_options_set_tls_server_name(secOpts, it) }
		sec_protocol_options_set_min_tls_protocol_version(secOpts, tls_protocol_version_TLSv12)

		when {
			!config.verifyCertificates -> {
				val queue =
					dispatch_queue_create("io.natskt.tls.verify", null)
						?: throw TlsException("dispatch_queue_create failed")
				val acceptAll: VerifyBlock = { _, _, complete -> complete?.invoke(true) }
				sec_protocol_options_set_verify_block(secOpts, acceptAll, queue)
			}
			config.trustAnchorsDer.isNotEmpty() -> {
				val anchors = config.trustAnchorsDer.toList()
				val queue =
					dispatch_queue_create("io.natskt.tls.verify", null)
						?: throw TlsException("dispatch_queue_create failed")
				val verifyAgainstAnchors: VerifyBlock = { _, secTrust, complete ->
					val ok = if (secTrust == null) false else evaluateAgainstAnchors(secTrust, anchors)
					complete?.invoke(ok)
				}
				sec_protocol_options_set_verify_block(
					secOpts,
					verifyAgainstAnchors,
					queue,
				)
			}
			// else: system trust roots — default Network.framework behavior, no verify block.
		}
	}

	// Pass NW_PARAMETERS_DEFAULT_CONFIGURATION for the TCP configurator — passing literal NULL
	// causes `nw_parameters_create_secure_tcp` to return nil. The default sentinel block tells
	// NW to add TCP with its default settings.
	return nw_parameters_create_secure_tcp(configureTls, _nw_parameters_configure_protocol_default_configuration)
		?: throw TlsException("nw_parameters_create_secure_tcp returned null")
}

private fun evaluateAgainstAnchors(
	secTrust: NSObject,
	anchorsDer: List<ByteArray>,
): Boolean {
	val trust = sec_trust_copy_ref(secTrust) ?: return false
	val parsedAnchors = mutableListOf<SecCertificateRef>()
	val anchorArray =
		CFArrayCreateMutable(kCFAllocatorDefault, anchorsDer.size.toLong(), null)
			?: run {
				CFRelease(trust)
				return false
			}
	try {
		for (anchorDer in anchorsDer) {
			val secCert = derToSecCertificate(anchorDer)
			parsedAnchors += secCert
			CFArrayAppendValue(anchorArray, secCert)
		}

		val setStatus = SecTrustSetAnchorCertificates(trust, anchorArray)
		if (setStatus != errSecSuccess) return false

		// Restrict to caller-provided anchors only — do not fall back to the system trust store.
		val onlyStatus = SecTrustSetAnchorCertificatesOnly(trust, true)
		if (onlyStatus != errSecSuccess) return false

		return memScoped {
			val errorVar = alloc<CFErrorRefVar>()
			val ok = SecTrustEvaluateWithError(trust, errorVar.ptr)
			errorVar.value?.let { CFRelease(it) }
			ok
		}
	} finally {
		parsedAnchors.forEach { CFRelease(it) }
		CFRelease(anchorArray)
		CFRelease(trust)
	}
}

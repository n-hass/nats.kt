@file:OptIn(ExperimentalForeignApi::class)

package io.natskt.tls.internal

import io.natskt.tls.NativeTlsConfigBuilder
import io.natskt.tls.openssl.SSL_CTX
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * Configures certificate-chain trust on [ctx] according to [config].
 *
 * Linux: configures OpenSSL's built-in X.509 verifier — `SSL_CTX_set_default_verify_paths` when no
 * caller anchors are supplied, otherwise an `X509_STORE` of the caller-provided anchors.
 *
 * Apple: installs a `SSL_CTX_set_cert_verify_callback` that routes the peer chain through
 * `SecTrustEvaluateWithError`. Caller-supplied anchors are applied via
 * `SecTrustSetAnchorCertificates` + `SecTrustSetAnchorCertificatesOnly(true)`; when no anchors are
 * supplied the system Keychain trust store is consulted.
 *
 * Returns a disposer the caller must invoke after the SSL session has been freed (used on Apple to
 * release the `StableRef` that backed the verify callback's `arg` pointer).
 */
internal expect fun configurePlatformTrust(
	ctx: CPointer<SSL_CTX>,
	config: NativeTlsConfigBuilder,
): () -> Unit

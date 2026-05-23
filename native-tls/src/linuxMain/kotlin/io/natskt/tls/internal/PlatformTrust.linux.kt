@file:OptIn(ExperimentalForeignApi::class)

package io.natskt.tls.internal

import io.ktor.network.tls.TlsException
import io.natskt.tls.NativeTlsConfigBuilder
import io.natskt.tls.openssl.SSL_CTX
import io.natskt.tls.openssl.SSL_CTX_set_cert_store
import io.natskt.tls.openssl.SSL_CTX_set_default_verify_paths
import io.natskt.tls.openssl.X509_STORE_add_cert
import io.natskt.tls.openssl.X509_STORE_free
import io.natskt.tls.openssl.X509_STORE_new
import io.natskt.tls.openssl.X509_free
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

internal actual fun configurePlatformTrust(
	ctx: CPointer<SSL_CTX>,
	config: NativeTlsConfigBuilder,
): () -> Unit {
	if (!config.verifyCertificates) return {}
	if (config.trustAnchorsDer.isEmpty()) {
		if (SSL_CTX_set_default_verify_paths(ctx) != 1) {
			throw TlsException("SSL_CTX_set_default_verify_paths failed")
		}
		return {}
	}
	val store = X509_STORE_new() ?: throw TlsException("X509_STORE_new failed")
	var transferred = false
	try {
		for (anchorDer in config.trustAnchorsDer) {
			val anchor = parseDerCert(anchorDer)
			val added = X509_STORE_add_cert(store, anchor)
			X509_free(anchor)
			if (added != 1) {
				throw TlsException("X509_STORE_add_cert failed")
			}
		}
		// SSL_CTX_set_cert_store transfers ownership of `store` to `ctx`.
		SSL_CTX_set_cert_store(ctx, store)
		transferred = true
	} finally {
		if (!transferred) X509_STORE_free(store)
	}
	return {}
}

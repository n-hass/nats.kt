@file:OptIn(ExperimentalForeignApi::class)

package io.natskt.tls.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.tls.TlsException
import io.natskt.tls.NativeTlsConfigBuilder
import io.natskt.tls.openssl.SSL_CTX
import io.natskt.tls.openssl.SSL_CTX_load_verify_locations
import io.natskt.tls.openssl.SSL_CTX_set_cert_store
import io.natskt.tls.openssl.X509_STORE_add_cert
import io.natskt.tls.openssl.X509_STORE_free
import io.natskt.tls.openssl.X509_STORE_new
import io.natskt.tls.openssl.X509_free
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.F_OK
import platform.posix.access
import platform.posix.getenv

private val logger = KotlinLogging.logger("PlatformTrust.linux")

private val certFileCandidates =
	listOf(
		"/etc/ssl/certs/ca-certificates.crt", // Debian, Ubuntu, Arch, Alpine
		"/etc/pki/tls/certs/ca-bundle.crt", // RHEL, Fedora, CentOS
		"/etc/ssl/ca-bundle.pem", // openSUSE
		"/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem",
		"/etc/ssl/cert.pem", // FreeBSD, Alpine
		"/var/lib/ca-certificates/ca-bundle.pem", // openSUSE
	)

private val certDirCandidates =
	listOf(
		"/etc/ssl/certs", // most distros
		"/etc/pki/tls/certs", // RHEL, Fedora, CentOS
	)

internal actual fun configurePlatformTrust(
	ctx: CPointer<SSL_CTX>,
	config: NativeTlsConfigBuilder,
): () -> Unit {
	if (!config.verifyCertificates) return {}
	if (config.trustAnchorsDer.isEmpty()) {
		val envFile = getenv("SSL_CERT_FILE")?.toKString()?.takeIf { it.isNotEmpty() }
		val envDir = getenv("SSL_CERT_DIR")?.toKString()?.takeIf { it.isNotEmpty() }
		val file = envFile ?: certFileCandidates.firstOrNull { access(it, F_OK) == 0 }
		val dir = envDir ?: certDirCandidates.firstOrNull { access(it, F_OK) == 0 }
		if (file == null && dir == null) {
			throw TlsException("no system CA trust store found in standard locations")
		}
		logger.trace { "loading trust store file=$file dir=$dir" }
		if (SSL_CTX_load_verify_locations(ctx, file, dir) != 1) {
			throw TlsException("SSL_CTX_load_verify_locations(file=$file, dir=$dir) failed")
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

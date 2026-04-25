@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.natskt.tls.cert

import io.natskt.tls.internal.TlsException
import io.natskt.tls.openssl.OPENSSL_sk_free
import io.natskt.tls.openssl.OPENSSL_sk_new_null
import io.natskt.tls.openssl.OPENSSL_sk_push
import io.natskt.tls.openssl.X509
import io.natskt.tls.openssl.X509_STORE
import io.natskt.tls.openssl.X509_STORE_CTX_free
import io.natskt.tls.openssl.X509_STORE_CTX_get_error
import io.natskt.tls.openssl.X509_STORE_CTX_init
import io.natskt.tls.openssl.X509_STORE_CTX_new
import io.natskt.tls.openssl.X509_STORE_free
import io.natskt.tls.openssl.X509_STORE_new
import io.natskt.tls.openssl.X509_STORE_set_default_paths
import io.natskt.tls.openssl.X509_check_host
import io.natskt.tls.openssl.X509_check_ip_asc
import io.natskt.tls.openssl.X509_free
import io.natskt.tls.openssl.X509_verify_cert
import io.natskt.tls.openssl.X509_verify_cert_error_string
import io.natskt.tls.openssl.d2i_X509
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value

internal actual fun validateCertificateChain(
	certs: List<ByteArray>,
	hostname: String?,
) {
	validateWithStore(certs, hostname, store = null)
}

/**
 * Validates a certificate chain against a trust store.
 * If [store] is null, creates a new store loaded with system default CAs.
 * If [store] is provided, uses it as-is (caller is responsible for freeing it).
 */
internal fun validateWithStore(
	certs: List<ByteArray>,
	hostname: String?,
	store: CPointer<X509_STORE>?,
) {
	if (certs.isEmpty()) throw TlsException("No certificates to validate")

	val ownStore = store == null
	val actualStore = store ?: X509_STORE_new() ?: throw TlsException("X509_STORE_new failed")
	if (ownStore) X509_STORE_set_default_paths(actualStore)

	val ctx =
		X509_STORE_CTX_new() ?: run {
			if (ownStore) X509_STORE_free(actualStore)
			throw TlsException("X509_STORE_CTX_new failed")
		}

	val x509Certs = mutableListOf<CPointer<X509>>()

	try {
		for (certDer in certs) {
			x509Certs.add(parseDerCert(certDer))
		}

		val leaf = x509Certs.first()

		val chain =
			if (x509Certs.size > 1) {
				val sk = OPENSSL_sk_new_null()
				for (i in 1 until x509Certs.size) {
					OPENSSL_sk_push(sk, x509Certs[i])
				}
				sk
			} else {
				null
			}

		try {
			val initResult = X509_STORE_CTX_init(ctx, actualStore, leaf, chain)
			if (initResult != 1) throw TlsException("X509_STORE_CTX_init failed")

			val verifyResult = X509_verify_cert(ctx)
			if (verifyResult != 1) {
				val err = X509_STORE_CTX_get_error(ctx)
				val errStr = X509_verify_cert_error_string(err.toLong())?.toKString() ?: "unknown error"
				throw TlsException("Certificate verification failed: $errStr")
			}

			if (hostname != null) {
				val result =
					if (hostname.isIpAddress()) {
						X509_check_ip_asc(leaf, hostname, 0u)
					} else {
						X509_check_host(leaf, hostname, hostname.length.toULong(), 0u, null)
					}
				if (result != 1) {
					throw TlsException("Certificate hostname mismatch: expected $hostname")
				}
			}
		} finally {
			if (chain != null) OPENSSL_sk_free(chain)
		}
	} finally {
		x509Certs.forEach { X509_free(it) }
		X509_STORE_CTX_free(ctx)
		if (ownStore) X509_STORE_free(actualStore)
	}
}

internal fun parseDerCert(certDer: ByteArray): CPointer<X509> =
	memScoped {
		val ptrVar = alloc<kotlinx.cinterop.CPointerVar<platform.posix.uint8_tVar>>()
		val pinned = certDer.pin()
		ptrVar.value = pinned.addressOf(0).reinterpret()
		val result = d2i_X509(null, ptrVar.ptr.reinterpret(), certDer.size.toLong())
		pinned.unpin()
		result
	} ?: throw TlsException("Failed to parse X.509 certificate")

private fun String.isIpAddress(): Boolean {
	// IPv6
	if (contains(':')) return true
	// IPv4: exactly 4 dot-separated decimal groups
	val parts = split('.')
	return parts.size == 4 &&
		parts.all { part ->
			part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() }
		}
}

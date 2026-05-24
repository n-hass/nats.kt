@file:OptIn(ExperimentalForeignApi::class)

package io.natskt.tls.internal

import io.ktor.network.tls.TlsException
import io.natskt.client.TlsPrivateKeyAlgorithm
import io.natskt.tls.NativeTlsConfig
import io.natskt.tls.openssl.EVP_PKEY
import io.natskt.tls.openssl.EVP_PKEY_EC
import io.natskt.tls.openssl.EVP_PKEY_RSA
import io.natskt.tls.openssl.EVP_PKEY_free
import io.natskt.tls.openssl.SSL_CTRL_CHAIN_CERT
import io.natskt.tls.openssl.SSL_CTX
import io.natskt.tls.openssl.SSL_CTX_check_private_key
import io.natskt.tls.openssl.SSL_CTX_ctrl
import io.natskt.tls.openssl.SSL_CTX_use_PrivateKey
import io.natskt.tls.openssl.SSL_CTX_use_certificate
import io.natskt.tls.openssl.X509_free
import io.natskt.tls.openssl.d2i_PrivateKey
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.posix.uint8_tVar

/**
 * Installs the client certificate chain and matching private key on [ctx] for mTLS.
 *
 * Mirrors the JVM-side wiring in `TcpTransportTls.jvm.kt`: leaf goes through
 * `SSL_CTX_use_certificate`, intermediates are appended via `SSL_CTX_ctrl(SSL_CTRL_CHAIN_CERT, 1, …)`
 * (the C-side `SSL_CTX_add1_chain_cert` macro), and the PKCS#8 DER key is decoded with
 * `d2i_PrivateKey` keyed on the declared algorithm.
 *
 * A no-op when [config] has no client identity configured.
 */
internal fun configureClientIdentity(
	ctx: CPointer<SSL_CTX>,
	config: NativeTlsConfig,
) {
	if (!config.hasClientCertificate) return

	val chain = config.clientCertificateChainDer
	val keyDer = config.clientPrivateKeyDer!!
	val algorithm = config.clientPrivateKeyAlgorithm!!

	val leaf = parseDerCert(chain[0])
	try {
		if (SSL_CTX_use_certificate(ctx, leaf) != 1) {
			throw TlsException("SSL_CTX_use_certificate failed for client leaf cert")
		}
	} finally {
		// use_certificate up-refs internally; release our local handle either way.
		X509_free(leaf)
	}

	for (idx in 1 until chain.size) {
		val intermediate = parseDerCert(chain[idx])
		try {
			val rc = SSL_CTX_ctrl(ctx, SSL_CTRL_CHAIN_CERT, 1L, intermediate.reinterpret<ByteVar>())
			if (rc != 1L) throw TlsException("SSL_CTX_add1_chain_cert failed at chain index $idx")
		} finally {
			X509_free(intermediate)
		}
	}

	val pkeyType =
		when (algorithm) {
			TlsPrivateKeyAlgorithm.Rsa -> EVP_PKEY_RSA
			TlsPrivateKeyAlgorithm.Ec -> EVP_PKEY_EC
		}
	val pkey = parsePrivateKeyDer(pkeyType, keyDer)
	try {
		if (SSL_CTX_use_PrivateKey(ctx, pkey) != 1) {
			throw TlsException("SSL_CTX_use_PrivateKey failed for client private key")
		}
	} finally {
		EVP_PKEY_free(pkey)
	}

	if (SSL_CTX_check_private_key(ctx) != 1) {
		throw TlsException("client certificate and private key do not match")
	}
}

private fun parsePrivateKeyDer(
	pkeyType: Int,
	keyDer: ByteArray,
): CPointer<EVP_PKEY> =
	memScoped {
		val ptrVar = alloc<CPointerVar<uint8_tVar>>()
		val pinned = keyDer.pin()
		ptrVar.value = pinned.addressOf(0).reinterpret()
		val result = d2i_PrivateKey(pkeyType, null, ptrVar.ptr.reinterpret(), keyDer.size.toLong())
		pinned.unpin()
		result
	} ?: throw TlsException("Failed to parse client private key (DER PKCS#8)")

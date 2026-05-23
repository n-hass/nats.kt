@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.natskt.tls.internal

import io.ktor.network.tls.TlsException
import io.natskt.tls.openssl.X509
import io.natskt.tls.openssl.d2i_X509
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.posix.uint8_tVar

/** Parses a single DER-encoded X.509 certificate. Caller must `X509_free` the returned pointer. */
internal fun parseDerCert(certDer: ByteArray): CPointer<X509> =
	memScoped {
		val ptrVar = alloc<CPointerVar<uint8_tVar>>()
		val pinned = certDer.pin()
		ptrVar.value = pinned.addressOf(0).reinterpret()
		val result = d2i_X509(null, ptrVar.ptr.reinterpret(), certDer.size.toLong())
		pinned.unpin()
		result
	} ?: throw TlsException("Failed to parse X.509 certificate")

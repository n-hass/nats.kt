@file:OptIn(
	kotlinx.cinterop.ExperimentalForeignApi::class,
	kotlinx.cinterop.BetaInteropApi::class,
)

package dev.nhass.ktor.tls.internal

import io.ktor.network.tls.TlsException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecCertificateRef
import platform.posix.uint8_tVar

/**
 * Wraps [this] as a CFData and passes it to [block].
 *
 * The pin only spans the [CFDataCreate] call: CFDataCreate copies the bytes into the CFDataRef,
 * so we `unpin` as soon as it returns — before [block] runs. After [block] returns, `CFRelease`
 * frees the CFDataRef.
 */
internal inline fun <R> ByteArray.asCFData(block: (CFDataRef) -> R): R {
	val pinned = pin()
	val cfData =
		try {
			CFDataCreate(
				kCFAllocatorDefault,
				pinned.addressOf(0).reinterpret<uint8_tVar>(),
				size.toLong(),
			) ?: throw TlsException("Failed to create CFData")
		} finally {
			pinned.unpin()
		}
	try {
		return block(cfData)
	} finally {
		CFRelease(cfData)
	}
}

/** Parses a single DER-encoded X.509 certificate. Caller must `CFRelease` the returned ref. */
internal fun derToSecCertificate(certDer: ByteArray): SecCertificateRef =
	certDer.asCFData { cfData ->
		SecCertificateCreateWithData(kCFAllocatorDefault, cfData)
			?: throw TlsException("Invalid X.509 certificate")
	}

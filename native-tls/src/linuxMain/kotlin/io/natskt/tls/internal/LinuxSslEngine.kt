@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.natskt.tls.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.SelectInterest
import io.ktor.network.selector.Selectable
import io.ktor.network.selector.SelectorManager
import io.ktor.network.tls.TlsException
import io.natskt.tls.openssl.ERR_error_string_n
import io.natskt.tls.openssl.ERR_get_error
import io.natskt.tls.openssl.SSL
import io.natskt.tls.openssl.SSL_CTX
import io.natskt.tls.openssl.SSL_CTX_free
import io.natskt.tls.openssl.SSL_connect
import io.natskt.tls.openssl.SSL_free
import io.natskt.tls.openssl.SSL_get_error
import io.natskt.tls.openssl.SSL_read
import io.natskt.tls.openssl.SSL_shutdown
import io.natskt.tls.openssl.SSL_write
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import platform.posix.ECONNRESET
import platform.posix.errno

private val logger = KotlinLogging.logger("LinuxSslEngine")

// OpenSSL error codes — stable across versions (see <openssl/ssl.h>).
private const val SSL_ERROR_NONE = 0
private const val SSL_ERROR_SSL = 1
private const val SSL_ERROR_WANT_READ = 2
private const val SSL_ERROR_WANT_WRITE = 3
private const val SSL_ERROR_SYSCALL = 5
private const val SSL_ERROR_ZERO_RETURN = 6

/**
 * Drives an `SSL*` against a non-blocking POSIX fd, suspending on the supplied [SelectorManager]
 * when OpenSSL signals `SSL_ERROR_WANT_READ` / `SSL_ERROR_WANT_WRITE`.
 *
 * Caller owns the lifecycle of [ssl] and [ctx]; they are freed by [close].
 *
 * The fd itself is owned by the Ktor socket. [selectable] is the same `Selectable` that backs the
 * socket so its descriptor is registered with [selectorManager] exactly once.
 */
internal class LinuxSslEngine(
	private val ssl: CPointer<SSL>,
	private val ctx: CPointer<SSL_CTX>,
	private val selectable: Selectable,
	private val selectorManager: SelectorManager,
) {
	private var closed: Boolean = false

	suspend fun handshake() {
		while (true) {
			val rc = SSL_connect(ssl)
			if (rc == 1) return
			when (val err = SSL_get_error(ssl, rc)) {
				SSL_ERROR_WANT_READ -> selectorManager.select(selectable, SelectInterest.READ)
				SSL_ERROR_WANT_WRITE -> selectorManager.select(selectable, SelectInterest.WRITE)
				else -> throw TlsException("SSL_connect failed: ${describeError(err)}")
			}
		}
	}

	/**
	 * Reads up to [length] bytes into [dst] starting at [offset]. Returns the number of bytes
	 * read, or `-1` if the peer closed the TLS session cleanly (`SSL_ERROR_ZERO_RETURN`).
	 */
	suspend fun read(
		dst: ByteArray,
		offset: Int,
		length: Int,
	): Int {
		if (closed) return -1
		val pinned = dst.pin()
		try {
			val ptr = pinned.addressOf(offset).reinterpret<ByteVar>()
			while (true) {
				val rc = SSL_read(ssl, ptr, length)
				if (rc > 0) return rc
				when (val err = SSL_get_error(ssl, rc)) {
					SSL_ERROR_ZERO_RETURN -> return -1
					SSL_ERROR_WANT_READ -> selectorManager.select(selectable, SelectInterest.READ)
					SSL_ERROR_WANT_WRITE -> selectorManager.select(selectable, SelectInterest.WRITE)
					SSL_ERROR_SYSCALL -> {
						// Per the OpenSSL docs: a return of 0 with WANT_NOTHING means the peer
						// closed without sending close_notify. Treat as EOF.
						if (rc == 0 || errno == ECONNRESET) return -1
						throw TlsException("SSL_read syscall failed (errno=$errno): ${describeError(err)}")
					}
					else -> throw TlsException("SSL_read failed: ${describeError(err)}")
				}
			}
			@Suppress("UNREACHABLE_CODE")
			return 0
		} finally {
			pinned.unpin()
		}
	}

	suspend fun write(
		src: ByteArray,
		offset: Int,
		length: Int,
	): Int {
		if (closed) throw TlsException("SSL session is closed")
		if (length == 0) return 0
		val pinned = src.pin()
		try {
			val ptr = pinned.addressOf(offset).reinterpret<ByteVar>()
			while (true) {
				val rc = SSL_write(ssl, ptr, length)
				if (rc > 0) return rc
				when (val err = SSL_get_error(ssl, rc)) {
					SSL_ERROR_WANT_READ -> selectorManager.select(selectable, SelectInterest.READ)
					SSL_ERROR_WANT_WRITE -> selectorManager.select(selectable, SelectInterest.WRITE)
					else -> throw TlsException("SSL_write failed: ${describeError(err)}")
				}
			}
			@Suppress("UNREACHABLE_CODE")
			return 0
		} finally {
			pinned.unpin()
		}
	}

	suspend fun shutdown() {
		if (closed) return
		// SSL_shutdown is a two-phase handshake. Returning 1 means both sides have closed; 0 means
		// our close_notify was sent but the peer hasn't replied. We do not block waiting for the
		// peer's close_notify — best-effort is sufficient.
		val rc = SSL_shutdown(ssl)
		if (rc < 0) {
			val err = SSL_get_error(ssl, rc)
			if (err == SSL_ERROR_WANT_READ || err == SSL_ERROR_WANT_WRITE) {
				// Peer hasn't responded yet; that's fine, just move on.
				logger.trace { "SSL_shutdown returned WANT_*; not waiting for peer close_notify" }
			} else {
				logger.debug { "SSL_shutdown returned $rc (${describeError(err)})" }
			}
		}
	}

	fun close() {
		if (closed) return
		closed = true
		SSL_free(ssl)
		SSL_CTX_free(ctx)
	}
}

internal fun describeError(sslErr: Int): String {
	val openSslErr = ERR_get_error()
	if (openSslErr == 0uL) {
		return when (sslErr) {
			SSL_ERROR_NONE -> "no error"
			SSL_ERROR_SSL -> "protocol error"
			SSL_ERROR_SYSCALL -> "syscall error (errno=$errno)"
			SSL_ERROR_ZERO_RETURN -> "clean shutdown"
			else -> "SSL error code $sslErr"
		}
	}
	return memScoped {
		val buf = ByteArray(256)
		val pinned = buf.pin()
		try {
			ERR_error_string_n(openSslErr, pinned.addressOf(0), buf.size.toULong())
			pinned.addressOf(0).reinterpret<ByteVar>().toKString()
		} finally {
			pinned.unpin()
		}
	}
}

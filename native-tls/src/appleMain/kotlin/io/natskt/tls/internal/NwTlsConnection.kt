@file:OptIn(
	kotlinx.cinterop.ExperimentalForeignApi::class,
	kotlinx.cinterop.BetaInteropApi::class,
)

package io.natskt.tls.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.tls.TlsException
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.pin
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreFoundation.CFErrorCopyDescription
import platform.CoreFoundation.CFRelease
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSString
import platform.Network._nw_content_context_default_stream
import platform.Network.nw_connection_cancel
import platform.Network.nw_connection_create
import platform.Network.nw_connection_receive
import platform.Network.nw_connection_send
import platform.Network.nw_connection_set_queue
import platform.Network.nw_connection_set_state_changed_handler
import platform.Network.nw_connection_start
import platform.Network.nw_connection_state_cancelled
import platform.Network.nw_connection_state_failed
import platform.Network.nw_connection_state_ready
import platform.Network.nw_connection_t
import platform.Network.nw_content_context_t
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_error_copy_cf_error
import platform.Network.nw_error_t
import platform.Network.nw_parameters_t
import platform.darwin.NSObject
import platform.darwin.dispatch_data_apply
import platform.darwin.dispatch_data_create
import platform.darwin.dispatch_data_get_size
import platform.darwin.dispatch_data_t
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import kotlin.concurrent.Volatile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val logger = KotlinLogging.logger("NwTlsConnection")

// Maximum payload nw_connection_receive will deliver per callback. Sized to the upper-bound
// max parameter so the scratch buffer never overflows.
private const val MAX_RECEIVE_FRAME = 65536

// Send-side buffer size. Drained from the app's ByteWriteChannel one chunk at a time.
private const val MAX_SEND_FRAME = 65536

// Bridges a `dispatch_*` pointer from `platform.darwin` (CPointer-flavored) to the
// `platform.Network`/`platform.Foundation` ObjC-bridged form (`NSObject?`).
private fun CPointer<*>.toNSObject(): NSObject = interpretObjCPointer(rawValue)

/**
 * Opens a TLS-from-start [nw_connection_t] to (host, port), drives the handshake to
 * [nw_connection_state_ready], and exposes the resulting stream as suspending Ktor channels.
 *
 * The connection owns its own dispatch queue; all NW callbacks fire on it. NW objects
 * (connection, parameters, dispatch types) are NSObject-bridged in Kotlin/Native and
 * GC-managed — no manual CFRelease required.
 */
internal class NwTlsConnection private constructor(
	private val connection: nw_connection_t,
	private val queue: dispatch_queue_t,
) {
	private val appInput = ByteChannel(autoFlush = true)
	private val appOutput = ByteChannel(autoFlush = true)

	val input: ByteReadChannel get() = appInput
	val output: ByteWriteChannel get() = appOutput

	private var receiveJob: Job? = null
	private var sendJob: Job? = null
	private var closed = false

	// Reused across every nw_connection_receive callback. Layout: [0] = isComplete flag,
	// [1..n] = payload. Safe to share across calls because awaitReceive is serialized by the
	// single-coroutine receive pump and writeFully copies into the channel before the next call.
	// Pinned for the connection's lifetime so the dispatch_data_apply callback can write straight
	// to addressOf(offset) without a pin/unpin toggle per frame.
	private val receiveScratch = ByteArray(MAX_RECEIVE_FRAME + 1)
	private val receiveScratchPin: Pinned<ByteArray> = receiveScratch.pin()

	private val sendBuf = ByteArray(MAX_SEND_FRAME)
	private val sendBufPin: Pinned<ByteArray> = sendBuf.pin()

	@Volatile
	private var pendingReceiveCont: CancellableContinuation<Int>? = null

	@Volatile
	private var pendingSendCont: CancellableContinuation<Unit>? = null

	// Same-thread (NW dispatch queue) state for the receive callback chain.
	private var pendingSendLength: Int = 0
	private var receiveCopyOffset: Int = 0

	private val onReceive: (dispatch_data_t, nw_content_context_t, Boolean, nw_error_t) -> Unit =
		{ content, _, isComplete, error ->
			val cont = pendingReceiveCont
			pendingReceiveCont = null
			if (cont != null) {
				if (error != null) {
					cont.resumeWithException(nwErrorToException("nw_connection_receive", error))
				} else {
					cont.resume(copyDispatchDataIntoScratch(content, isComplete))
				}
			}
		}

	private val onSend: (nw_error_t) -> Unit = { error ->
		val cont = pendingSendCont
		pendingSendCont = null
		if (cont != null) {
			if (error != null) {
				cont.resumeWithException(nwErrorToException("nw_connection_send", error))
			} else {
				cont.resume(Unit)
			}
		}
	}

	@OptIn(UnsafeNumber::class)
	private val dispatchApplier: (dispatch_data_t, ULong, COpaquePointer?, ULong) -> Boolean =
		{ _, _, buffer, size ->
			val len = size.toInt()
			if (buffer != null && len > 0) {
				platform.posix.memcpy(receiveScratchPin.addressOf(receiveCopyOffset), buffer, size)
				receiveCopyOffset += len
			}
			true
		}

	private val receiveSetup: (CancellableContinuation<Int>) -> Unit = { cont ->
		pendingReceiveCont = cont
		nw_connection_receive(connection, 1u, MAX_RECEIVE_FRAME.toUInt(), onReceive)
	}

	@OptIn(UnsafeNumber::class)
	private val sendSetup: (CancellableContinuation<Unit>) -> Unit = { cont ->
		pendingSendCont = cont
		// Null destructor: libdispatch copies the buffer internally, so the lifetime-pin on
		// sendBuf is enough — we don't need to hold a per-send pin past this call.
		val data =
			dispatch_data_create(
				sendBufPin.addressOf(0),
				pendingSendLength.toULong(),
				queue,
				null,
			)
		nw_connection_send(connection, data, _nw_content_context_default_stream, false, onSend)
	}

	private fun startPumps(scope: CoroutineScope) {
		receiveJob =
			scope.launch {
				try {
					while (true) {
						// receiveScratch[0] = isComplete flag (1 = peer closed),
						// receiveScratch[1..n] = payload. Returned [n] is total bytes incl. flag.
						val n = awaitReceive()
						if (n > 1) {
							appInput.writeFully(receiveScratch, 1, n)
							appInput.flush()
						}
						if (receiveScratch[0] == 1.toByte()) break
					}
					appInput.flushAndClose()
				} catch (cause: Throwable) {
					appInput.cancel(cause)
				}
			}

		sendJob =
			scope.launch {
				try {
					while (true) {
						val n = appOutput.readAvailable(sendBuf, 0, sendBuf.size)
						if (n == -1) break
						if (n == 0) continue
						sendBytes(n)
					}
				} catch (cause: Throwable) {
					appOutput.cancel(cause)
				}
			}
	}

	/** Fills [receiveScratch] in place; returns total bytes written (including the flag byte). */
	private suspend fun awaitReceive(): Int = suspendCancellableCoroutine(receiveSetup)

	private suspend fun sendBytes(length: Int) {
		pendingSendLength = length
		suspendCancellableCoroutine(sendSetup)
	}

	@OptIn(UnsafeNumber::class)
	private fun copyDispatchDataIntoScratch(
		data: dispatch_data_t,
		isComplete: Boolean,
	): Int {
		val total = data?.let { dispatch_data_get_size(it).toInt() } ?: 0
		receiveScratch[0] = if (isComplete) 1 else 0
		if (data == null || total == 0) return 1
		receiveCopyOffset = 1
		dispatch_data_apply(data, dispatchApplier)
		return total + 1
	}

	suspend fun close() {
		if (closed) return
		closed = true
		try {
			nw_connection_cancel(connection)
		} finally {
			receiveJob?.cancelAndJoin()
			sendJob?.cancelAndJoin()
			// Both pumps have exited and NW has fired any in-flight callbacks (with errors) by
			// the time cancelAndJoin returns, so it's safe to release the lifetime pins.
			receiveScratchPin.unpin()
			sendBufPin.unpin()
		}
	}

	companion object {
		suspend fun open(
			host: String,
			port: String,
			parameters: nw_parameters_t,
			scope: CoroutineScope,
		): NwTlsConnection {
			val endpoint =
				nw_endpoint_create_host(host, port)
					?: throw TlsException("nw_endpoint_create_host('$host', '$port') failed")
			val connection =
				nw_connection_create(endpoint, parameters)
					?: throw TlsException("nw_connection_create failed")

			val queue =
				dispatch_queue_create("io.natskt.tls.nw", null)
					?: throw TlsException("dispatch_queue_create returned null")
			nw_connection_set_queue(connection, queue)

			val ready = CompletableDeferred<Unit>()
			nw_connection_set_state_changed_handler(connection) { state, error ->
				when (state) {
					nw_connection_state_ready -> ready.complete(Unit)
					nw_connection_state_failed -> {
						val cause =
							error?.let { nwErrorToException("nw_connection failed", it) }
								?: TlsException("nw_connection failed")
						ready.completeExceptionally(cause)
					}
					nw_connection_state_cancelled -> {
						if (!ready.isCompleted) {
							ready.completeExceptionally(TlsException("nw_connection cancelled before ready"))
						}
					}
					else -> { /* waiting / preparing — keep listening */ }
				}
			}
			nw_connection_start(connection)

			try {
				ready.await()
			} catch (cause: Throwable) {
				nw_connection_cancel(connection)
				throw cause
			}
			logger.trace { "NW TLS connection ready to $host:$port" }

			return NwTlsConnection(connection, queue).also { it.startPumps(scope) }
		}
	}
}

private fun nwErrorToException(
	message: String,
	error: nw_error_t,
): Throwable {
	val cfErr = nw_error_copy_cf_error(error)
	val desc =
		if (cfErr != null) {
			val descRef = CFErrorCopyDescription(cfErr)
			val s = (CFBridgingRelease(descRef) as? NSString)?.toString() ?: "unknown"
			CFRelease(cfErr)
			s
		} else {
			"unknown"
		}
	return TlsException("$message: $desc")
}

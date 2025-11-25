package io.natskt.api.internal

import io.natskt.api.CloseReason
import io.natskt.api.ConnectionState
import io.natskt.internal.ClientOperation
import io.natskt.internal.ServerOperation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

internal interface ProtocolEngine {
	val serverInfo: MutableStateFlow<ServerOperation.InfoOp?>

	/** Hot state; reflects transitions Connecting→Connected→… */
	val state: StateFlow<ConnectionState>

	/** Queue a client op; serialized by a dedicated writer coroutine. */
	suspend fun send(op: ClientOperation)

	/** Start handshake and reader/writer; returns when fully Connected or throws on failure. */
	suspend fun start(): Unit

	suspend fun ping(): Unit

	suspend fun flush()

	/** Begin protocol drain (UNSUBs/Flush) but do NOT close transport; returns when drained or timeout. */
	suspend fun drain(timeout: Duration)

	/** Close immediately (best-effort flush); idempotent. */
	suspend fun close()

	/** Completes once reader+writer coroutines finish and transport is closed. */
	val closed: Deferred<CloseReason>

	companion object {
		val Empty =
			object : ProtocolEngine {
				override val serverInfo = MutableStateFlow<ServerOperation.InfoOp?>(null)
				override val state: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Uninitialised)

				override suspend fun send(op: ClientOperation) { }

				override suspend fun start() { }

				override suspend fun ping() { }

				override suspend fun flush() { }

				override suspend fun drain(timeout: Duration) { }

				override suspend fun close() { }

				override val closed: Deferred<CloseReason> = CompletableDeferred()
			}
	}
}

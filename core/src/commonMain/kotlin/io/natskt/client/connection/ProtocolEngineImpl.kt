@file:OptIn(ExperimentalTime::class)

package io.natskt.client.connection

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.write
import io.natskt.api.CloseReason
import io.natskt.api.ConnectionPhase
import io.natskt.api.ConnectionState
import io.natskt.api.internal.ClientOperation
import io.natskt.api.internal.Operation
import io.natskt.api.internal.OperationSerializer
import io.natskt.api.internal.ProtocolEngine
import io.natskt.api.internal.ServerOperation
import io.natskt.client.NatsServerAddress
import io.natskt.client.transport.Transport
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.wireJsonFormat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val logger = KotlinLogging.logger { }

internal class ProtocolEngineImpl(
	private val transportFactory: TransportFactory,
	private val address: NatsServerAddress,
	private val parser: OperationSerializer,
	private val scope: CoroutineScope,
) : ProtocolEngine {
	override val events: SharedFlow<ServerOperation>
		get() = _events
	private val _events = MutableSharedFlow<ServerOperation>()

	override val state: StateFlow<ConnectionState>
		get() = _state
	private val _state = MutableStateFlow(ConnectionState.Uninitialised)

	private var transport: Transport? = null

	private var rttMeasureStart: Instant? = null

	override suspend fun send(op: ClientOperation) {
		val msg = parser.encode(op)

		if (transport == null) {
			throw IllegalStateException("cannot send with no connection open")
		}

		logger.trace { "sending ${op::class.simpleName}" }
		transport!!.writeAndFlush(msg)
	}

	override suspend fun start() {
		_state.update { phase = ConnectionPhase.Connecting }
		transport =
			runCatching {
				transportFactory.connect(address, currentCoroutineContext())
			}.getOrElse {
				_state.update { phase = ConnectionPhase.Failed }
				_closed.complete(CloseReason.IoError(it))
				return
			}

		val incoming = transport!!.incoming

		when (parser.parse(incoming)) {
			is ServerOperation.InfoOp -> {
				val connect =
					ClientOperation.ConnectOp(
						verbose = true,
						pedantic = false,
						tlsRequired = false,
						authToken = null,
						user = null,
						pass = null,
						name = null,
						protocol = null,
						echo = false,
						sig = null,
						jwt = null,
						noResponders = null,
						headers = true,
						nkey = null,
					)
				send(connect)
			}
			else -> {
				_closed.complete(CloseReason.ProtocolError("Server did not open connection with an INFO operation"))
				return
			}
		}

		when (parser.parse(incoming)) {
			is Operation.Ok -> {
				_state.update { phase = ConnectionPhase.Connected }
			}
			else -> {
				_closed.complete(CloseReason.ProtocolError("Server did not respond OK to CONNECT"))
				return
			}
		}
		scope.launch {
			var op: Operation?
			
			send(
				ClientOperation.SubOp(
					subject = "test.echo",
					queueGroup = null,
					sid = "11",
				),
			)
			
			while (!incoming.isClosedForRead) {
				op = parser.parse(incoming)

				if (op !is ServerOperation) {
					when (op) {
						Operation.Pong -> {
							_state.update {
								lastPongAt = Clock.System.now().toEpochMilliseconds()
								rtt =
									rttMeasureStart
										?.let { Clock.System.now() - it }
										?.inWholeMicroseconds
										?.toDouble()
										?.let { it / 1000 }
								rttMeasureStart = null
							}
						}
						Operation.Ping -> {
							send(Operation.Pong)
							_state.update {
								lastPingAt = Clock.System.now().toEpochMilliseconds()
							}

							send(
								ClientOperation.PubOp(
									subject = "test.echo",
									replyTo = null,
									payload = "this is a test message on ping".encodeToByteArray(),
								),
							)
						}
						is Operation.Err -> {
							logger.error { "received a protocol error response: ${(op as Operation.Err).message}" }
						}
						Operation.Ok -> { }
						else -> {
							logger.error { "idk: $op" }
						}
					}

					continue
				}

				_events.emit(op)
			}
		}
	}

	override suspend fun ping() {
		rttMeasureStart = Clock.System.now()
		send(Operation.Ping)
	}

	override suspend fun drain(timeout: Duration) {
		TODO("Not yet implemented")
	}

	override suspend fun close() {
		if (transport == null) {
			throw IllegalStateException("Cannot close connection as it is not open")
		}

		_closed.complete(CloseReason.CleanClose)

		transport!!.close()
	}

	override val closed: Deferred<CloseReason>
		get() = _closed
	private val _closed = CompletableDeferred<CloseReason>()

	private suspend fun MutableStateFlow<ConnectionState>.update(block: ConnectionState.() -> Unit) = _state.emit(_state.value.copy().apply { block() })
}

private suspend fun Transport.writeAndFlush(msg: ByteArray) {
	var written = 0
	write {
		while (written < msg.size) {
			it.write(msg.size - written) { buffer, low, high ->
				var writtenHere = 0
				for (i in low..high) {
					if (written >= msg.size) break
					buffer[i] = msg[written]
					written++
					writtenHere++
				}
				writtenHere
			}
		}
	}
	flush()
}

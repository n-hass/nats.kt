@file:OptIn(ExperimentalTime::class)

package io.natskt.client.connection

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.write
import io.natskt.api.CloseReason
import io.natskt.api.ConnectionPhase
import io.natskt.api.ConnectionState
import io.natskt.api.internal.ClientOperation
import io.natskt.api.internal.InternalSubscriptionHandler
import io.natskt.api.internal.MessageInternal
import io.natskt.api.internal.Operation
import io.natskt.api.internal.OperationSerializer
import io.natskt.api.internal.ParsedOutput
import io.natskt.api.internal.ProtocolEngine
import io.natskt.api.internal.ServerOperation
import io.natskt.client.NatsServerAddress
import io.natskt.client.transport.Transport
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.connectionCoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
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
	private val subscriptions: Map<String, InternalSubscriptionHandler>,
	override val serverInfo: MutableStateFlow<ServerOperation.InfoOp?>,
	private val scope: CoroutineScope,
) : ProtocolEngine {
	override val state = MutableStateFlow(ConnectionState.Uninitialised)
	override val closed = CompletableDeferred<CloseReason>()

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
		state.update { phase = ConnectionPhase.Connecting }
		transport =
			runCatching {
				transportFactory.connect(address, currentCoroutineContext())
			}.getOrElse {
				state.update { phase = ConnectionPhase.Failed }
				closed.complete(CloseReason.IoError(it))
				return
			}

		val incoming = transport!!.incoming

		when (parser.parse(incoming)) {
			is ServerOperation.InfoOp -> {
				val connect =
					ClientOperation.ConnectOp(
						verbose = false,
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
				with(connectionCoroutineDispatcher) {
					send(connect)
				}
			}
			else -> {
				closed.complete(CloseReason.ProtocolError("Server did not open connection with an INFO operation"))
				return
			}
		}

		state.update { phase = ConnectionPhase.Connected }

		scope.launch {
			var out: ParsedOutput?

			while (!incoming.isClosedForRead) {
				out = parser.parse(incoming)

				if (out is MessageInternal) {
					subscriptions[out.sid]?.emit(out)
					continue
				}

				if (out !is ServerOperation) {
					when (out) {
						Operation.Pong -> {
							state.update {
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
							state.update {
								lastPingAt = Clock.System.now().toEpochMilliseconds()
							}
						}
						is Operation.Err -> {
							logger.error { "received a protocol error response: ${(out as Operation.Err).message}" }
						}
						Operation.Ok -> { }
						Operation.Empty -> {
							transport?.close()
							closed.complete(CloseReason.ServerInitiatedClose)
						}
						else -> {
							logger.error { "idk: $out" }
						}
					}

					continue
				}

				if (out is ServerOperation.InfoOp) {
					serverInfo.emit(out)
				}
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
		closed.complete(CloseReason.CleanClose)
		transport!!.close()
	}

	private fun MutableStateFlow<ConnectionState>.update(block: ConnectionState.() -> Unit) {
		this.value = this.value.copy().apply { block() }
	}
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

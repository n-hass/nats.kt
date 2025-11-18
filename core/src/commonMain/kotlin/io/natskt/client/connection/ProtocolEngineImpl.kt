@file:OptIn(ExperimentalTime::class, InternalNatsApi::class)

package io.natskt.client.connection

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.ConcurrentMap
import io.ktor.utils.io.write
import io.natskt.api.CloseReason
import io.natskt.api.ConnectionPhase
import io.natskt.api.ConnectionState
import io.natskt.api.Credentials
import io.natskt.api.internal.InternalNatsApi
import io.natskt.api.internal.OperationSerializer
import io.natskt.api.internal.ProtocolEngine
import io.natskt.client.NatsServerAddress
import io.natskt.client.transport.Transport
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.ClientOperation
import io.natskt.internal.InternalSubscriptionHandler
import io.natskt.internal.MessageInternal
import io.natskt.internal.Operation
import io.natskt.internal.ParsedOutput
import io.natskt.internal.PendingRequest
import io.natskt.internal.ServerOperation
import io.natskt.internal.connectionCoroutineDispatcher
import io.natskt.nkeys.NKeySeed
import io.natskt.nkeys.NKeys
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
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
	private val pendingRequests: ConcurrentMap<String, PendingRequest>,
	override val serverInfo: MutableStateFlow<ServerOperation.InfoOp?>,
	private val credentials: Credentials?,
	private val tlsRequired: Boolean,
	private val scope: CoroutineScope,
) : ProtocolEngine {
	override val state = MutableStateFlow(ConnectionState.Uninitialised)
	override val closed = CompletableDeferred<CloseReason>()

	private var transport: Transport? = null

	private var rttMeasureStart: Instant? = null

	private data class AuthPayload(
		val authToken: String? = null,
		val user: String? = null,
		val pass: String? = null,
		val jwt: String? = null,
		val signature: String? = null,
		val nkey: String? = null,
	)

	private fun buildConnectOp(info: ServerOperation.InfoOp): ClientOperation.ConnectOp {
		val auth = resolveAuth(info)
		return ClientOperation.ConnectOp(
			verbose = false,
			pedantic = false,
			tlsRequired = tlsRequired,
			authToken = auth.authToken,
			user = auth.user,
			pass = auth.pass,
			name = null,
			protocol = null,
			echo = false,
			sig = auth.signature,
			jwt = auth.jwt,
			noResponders = null,
			headers = true,
			nkey = auth.nkey,
		)
	}

	private fun resolveAuth(info: ServerOperation.InfoOp): AuthPayload {
		val urlUser = address.url.user?.takeIf { it.isNotBlank() }
		val urlPassword = address.url.password?.takeIf { it.isNotBlank() }

		val creds = credentials ?: return AuthPayload(user = urlUser, pass = urlPassword)
		return when (creds) {
			is Credentials.Password -> {
				val user = creds.username.takeUnless { it.isBlank() } ?: urlUser
				val pass = creds.password.takeUnless { it.isBlank() } ?: urlPassword
				AuthPayload(user = user, pass = pass)
			}
			is Credentials.Jwt -> buildNKeyAuth(info, creds.nkey, creds.token)
			is Credentials.Nkey -> buildNKeyAuth(info, creds.key, null)
			is Credentials.File -> {
				val parsed = NKeys.parseCreds(creds.content)
				buildNKeyAuth(info, parsed.seed, parsed.jwt)
			}
		}
	}

	private fun buildNKeyAuth(
		info: ServerOperation.InfoOp,
		seedValue: String,
		jwt: String?,
	): AuthPayload {
		if (seedValue.isBlank()) {
			throw IllegalArgumentException("NKey seed cannot be blank")
		}
		return buildNKeyAuth(info, NKeys.parseSeed(seedValue), jwt)
	}

	private fun buildNKeyAuth(
		info: ServerOperation.InfoOp,
		seed: NKeySeed,
		jwt: String?,
	): AuthPayload {
		if (info.nonce == null) {
			return AuthPayload()
		}
		val signature = seed.signNonce(info.nonce!!)
		return AuthPayload(jwt = jwt, signature = signature, nkey = seed.publicKey)
	}

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
				transportFactory.connect(address, scope.coroutineContext)
			}.getOrElse {
				state.update { phase = ConnectionPhase.Failed }
				closed.complete(CloseReason.IoError(it))
				return
			}

		transport!!.incoming

		when (val info = parser.parse(transport!!.incoming)) {
			is ServerOperation.InfoOp -> {
				serverInfo.value = info
				if (info.ldm == true) {
					enterLameDuckMode()
					return
				}
				if ((info.tlsRequired == true) || tlsRequired) {
					logger.trace { "upgrading connection to TLS" }
					transport = transport!!.upgradeTLS()
				}
				val connect =
					runCatching { buildConnectOp(info) }
						.getOrElse {
							logger.error(it) { "failed to prepare CONNECT for ${address.url}" }
							state.update { phase = ConnectionPhase.Failed }
							transport?.close()
							closed.complete(CloseReason.HandshakeRejected)
							return
						}
				with(connectionCoroutineDispatcher) { send(connect) }
			}
			else -> {
				closed.complete(CloseReason.ProtocolError("Server did not open connection with an INFO operation"))
				return
			}
		}

		state.update { phase = ConnectionPhase.Connected }

		scope.launch {
			var out: ParsedOutput?

			while (!transport!!.incoming.isClosedForRead) {
				out = parser.parse(transport!!.incoming)

				if (out is MessageInternal) {
					val pending = pendingRequests.remove(out.sid)
					if (pending != null) {
						if (pending.continuation.isActive) {
							pending.continuation.resume(out)
						}
						continue
					}

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
					if (out.ldm == true) {
						enterLameDuckMode()
						break
					}
				}
			}
		}
	}

	override suspend fun ping() {
		rttMeasureStart = Clock.System.now()
		send(Operation.Ping)
	}

	override suspend fun drain(timeout: Duration) {
		withTimeoutOrNull(timeout) {
			subscriptions.forEach {
				it.value.close()
			}
		}
		when (val transport = this.transport) {
			null -> {}
			else -> {
				transport.flush()
			}
		}
	}

	override suspend fun close() {
		if (transport == null) {
			throw IllegalStateException("Cannot close connection as it is not open")
		}
		closed.complete(CloseReason.CleanClose)
		transport!!.flush()
		transport!!.close()
	}

	private suspend fun enterLameDuckMode() {
		logger.debug { "server ${address.url} entered lame duck mode" }
		state.update { phase = ConnectionPhase.LameDuck }
		if (!closed.isCompleted) {
			closed.complete(CloseReason.LameDuckMode)
		}
		runCatching { transport?.flush() }
		runCatching { transport?.close() }
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

@file:OptIn(ExperimentalTime::class)

package io.natskt.client.connection

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url
import io.ktor.util.collections.ConcurrentMap
import io.natskt.api.CloseReason
import io.natskt.api.ConnectionState
import io.natskt.api.internal.ProtocolEngine
import io.natskt.client.ClientConfiguration
import io.natskt.client.NatsServerAddress
import io.natskt.internal.ClientOperation
import io.natskt.internal.InternalSubscriptionHandler
import io.natskt.internal.PendingRequest
import io.natskt.internal.ServerOperation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

private val logger = KotlinLogging.logger { }

internal const val LAME_DUCK_BACKOFF_MILLIS: Long = 60_000

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConnectionManagerImpl(
	val config: ClientConfiguration,
	val subscriptions: ConcurrentMap<String, InternalSubscriptionHandler>,
	val pendingRequests: ConcurrentMap<String, PendingRequest>,
) {
	internal val current: MutableStateFlow<ProtocolEngine> = MutableStateFlow(ProtocolEngine.Empty)

	val connectionState: StateFlow<ConnectionState> = current.flatMapLatest { it.state }.stateIn(config.scope, SharingStarted.WhileSubscribed(), current.value.state.value)

	val serverInfo = MutableStateFlow<ServerOperation.InfoOp?>(null)

	private val allServers = mutableSetOf<NatsServerAddress>().apply { addAll(config.servers) }
	private val lameDuckServers = mutableMapOf<NatsServerAddress, Long>()

	private var failureCount = 0

	private var reconnectJob: Job? = null

	fun start() {
		reconnectJob =
			config.scope.launch {
				while (config.maxReconnects == null || failureCount < config.maxReconnects) {
					val address = selectAddress()
					current.emit(
						ProtocolEngineImpl(
							config.transportFactory,
							address,
							config.parser,
							subscriptions,
							pendingRequests,
							serverInfo,
							config.credentials,
							config.tlsRequired,
							config.writeBufferLimitBytes,
							config.writeFlushIntervalMs,
							config.scope,
						),
					)

					val eventJob =
						config.scope
							.launch {
								serverInfo.collect {
									when (it) {
										is ServerOperation.InfoOp -> {
											val newServers =
												it.connectUrls
													?.map { url ->
														NatsServerAddress(Url(url))
													}.orEmpty()

											if (newServers.isNotEmpty()) {
												allServers.clear()
												allServers.add(address)
												allServers.addAll(newServers)
											} else if (allServers.isEmpty()) {
												allServers.add(address)
											}
											if (it.ldm == true) {
												markLameDuck(address)
											}
										}
										else -> { }
									}
								}
							}

					current.value.start()

					if (!current.value.closed.isCompleted) {
						current.value.ping()
					}

					val closed = current.value.closed.await()
					logger.debug { "closed. reason: $closed" }
					eventJob.cancel()
					when (closed) {
						is CloseReason.IoError, CloseReason.HandshakeRejected -> failureCount++
						else -> failureCount = 0
					}
					delay(config.reconnectDebounceMs)
				}
				logger.debug { "maxReconnects exceeded" }
			}
	}

	suspend fun stop() {
		reconnectJob?.cancel() ?: logger.warn { "closing connection pool, but no manager job is active" }
		current.value.close()
	}

	suspend fun send(op: ClientOperation) = current.value.send(op)

	internal fun markLameDuck(
		address: NatsServerAddress,
		timestamp: Long = Clock.System.now().toEpochMilliseconds(),
	) {
		lameDuckServers[address] = timestamp
	}

	internal fun selectAddress(now: Long = Clock.System.now().toEpochMilliseconds()): NatsServerAddress {
		if (allServers.isEmpty()) {
			throw IllegalStateException("no servers available")
		}
		pruneLameDuck(now)
		val viable =
			allServers.filterNot { address ->
				val markedAt = lameDuckServers[address]
				markedAt != null && now - markedAt < LAME_DUCK_BACKOFF_MILLIS
			}
		val pool = if (viable.isNotEmpty()) viable else allServers.toList()
		return pool.shuffled().first()
	}

	private fun pruneLameDuck(now: Long) {
		lameDuckServers.removeIf { entry ->
			now - entry.value >= LAME_DUCK_BACKOFF_MILLIS
		}
	}

	suspend fun ping() = current.value.ping()

	suspend fun drain(timeout: Duration) = current.value.drain(timeout)

	suspend fun flush() = current.value.flush()
}

private fun <K, V> MutableMap<K, V>.removeIf(predicate: (Map.Entry<K, V>) -> Boolean) =
	forEach {
		if (predicate(it)) remove(it.key)
	}

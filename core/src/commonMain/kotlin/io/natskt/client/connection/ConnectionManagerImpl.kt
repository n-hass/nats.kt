package io.natskt.client.connection

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url
import io.natskt.api.CloseReason
import io.natskt.api.ConnectionState
import io.natskt.api.internal.ClientOperation
import io.natskt.api.internal.InternalSubscriptionHandler
import io.natskt.api.internal.ProtocolEngine
import io.natskt.api.internal.ServerOperation
import io.natskt.client.ClientConfiguration
import io.natskt.client.NatsServerAddress
import io.natskt.internal.connectionCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger { }

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConnectionManagerImpl(
	val config: ClientConfiguration,
	val subscriptions: Map<String, InternalSubscriptionHandler>,
) {
	private val scope: CoroutineScope =
		config.scope ?: CoroutineScope(connectionCoroutineDispatcher + SupervisorJob() + CoroutineName("ConnectionManager"))

	internal val current: MutableStateFlow<ProtocolEngine> = MutableStateFlow(ProtocolEngine.Empty)

	val connectionStatus: StateFlow<ConnectionState> = current.flatMapLatest { it.state }.stateIn(scope, SharingStarted.WhileSubscribed(), current.value.state.value)

	val serverInfo = MutableStateFlow<ServerOperation.InfoOp?>(null)

	private val allServers = mutableSetOf<NatsServerAddress>().apply { addAll(config.servers) }

	private var failureCount = 0

	private var reconnectJob: Job? = null

	fun start() {
		reconnectJob =
			scope.launch {
				while (config.maxReconnects == null || failureCount < config.maxReconnects) {
					val address = allServers.shuffled().first()
					current.emit(
						ProtocolEngineImpl(
							config.transportFactory,
							address,
							config.parser,
							subscriptions,
							serverInfo,
							scope,
						),
					)

					val eventJob =
						scope
							.launch {
								serverInfo.collect {
									when (it) {
										is ServerOperation.InfoOp -> {
											val newServers =
												it.connectUrls
													?.map { url ->
														NatsServerAddress(Url(url))
													}.orEmpty()

											allServers.addAll(newServers)
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
}

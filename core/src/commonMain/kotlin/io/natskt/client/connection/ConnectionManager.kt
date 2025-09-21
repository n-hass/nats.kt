package io.natskt.client.connection

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url
import io.natskt.api.CloseReason
import io.natskt.api.ConnectionState
import io.natskt.api.internal.ClientOperation
import io.natskt.api.internal.Operation
import io.natskt.api.internal.ProtocolEngine
import io.natskt.api.internal.ServerOperation
import io.natskt.client.ClientConfiguration
import io.natskt.client.NatsServerAddress
import io.natskt.internal.connectionCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger { }

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConnectionManager(
	val config: ClientConfiguration,
) {
	private val scope: CoroutineScope =
		config.scope ?: CoroutineScope(connectionCoroutineDispatcher + SupervisorJob() + CoroutineName("ConnectionManager"))

	internal val current: MutableStateFlow<ProtocolEngine> = MutableStateFlow(ProtocolEngine.Empty)

	val connectionStatus: StateFlow<ConnectionState> = current.flatMapLatest { it.state }.stateIn(scope, SharingStarted.WhileSubscribed(), current.value.state.value)

	val events: SharedFlow<Operation> = current.flatMapLatest { it.events }.shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)

	private val allServers = mutableSetOf<NatsServerAddress>().apply { addAll(config.servers) }

	private var failureCount = 0

	fun start() {
		scope.launch {
			while (config.maxReconnects == null || failureCount < config.maxReconnects) {
				val address = allServers.shuffled().first()
				current.value =
					ProtocolEngineImpl(
						config.transportFactory,
						address,
						config.parser,
						scope,
					)

				val eventJob =
					scope
						.launch {
							current.value.events.collect {
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
								logger.debug { ("event: $it") }
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
			}
			logger.debug { "maxReconnects exceeded" }
		}
	}

	suspend fun send(op: ClientOperation) = current.value.send(op)
}

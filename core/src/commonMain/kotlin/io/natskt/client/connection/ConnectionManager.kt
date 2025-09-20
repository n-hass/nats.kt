package io.natskt.client.connection

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.Url
import io.natskt.api.CloseReason
import io.natskt.api.ConnectionState
import io.natskt.api.internal.ServerOperation
import io.natskt.client.ClientConfiguration
import io.natskt.client.NatsServerAddress
import io.natskt.internal.connectionCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch

private val logger = KotlinLogging.logger { }

internal class ConnectionManager(
	val config: ClientConfiguration,
) {
	private val scope: CoroutineScope =
		config.scope ?: CoroutineScope(connectionCoroutineDispatcher + SupervisorJob() + CoroutineName("ConnectionManager"))

	val connectionStatus: StateFlow<ConnectionState>
		get() = _connectionStatus
	private val _connectionStatus = MutableStateFlow(ConnectionState.Uninitialised)

	private val allServers = mutableSetOf<NatsServerAddress>().apply { addAll(config.servers) }

	private var failureCount = 0

	fun start() {
		scope.launch {
			while (config.maxReconnects == null || failureCount < config.maxReconnects) {
				val address = allServers.shuffled().first()
				val connection =
					ProtocolEngineImpl(
						config.transportFactory,
						address,
						config.parser,
						scope,
					)

				val j1 =
					scope
						.launch {
							_connectionStatus.emitAll(connection.state)
						}

				val j2 =
					scope
						.launch {
							connection.events.collect {
								when (it) {
									is ServerOperation.InfoOp -> {
										val newServers =
											it.connectUrls
												?.map { url ->
													NatsServerAddress(Url(url))
												}.orEmpty()

										allServers.addAll(newServers)
									}
								}
								logger.debug { ("event: $it") }
							}
						}

				connection.start()

				if (!connection.closed.isCompleted) {
					connection.ping()
				}

				val closed = connection.closed.await()
				logger.debug { "closed. reason: $closed" }
				j1.cancel()
				j2.cancel()
				when (closed) {
					is CloseReason.IoError, CloseReason.HandshakeRejected -> failureCount++
					else -> failureCount = 0
				}
			}
			logger.debug { "maxReconnects exceeded" }
		}
	}
}

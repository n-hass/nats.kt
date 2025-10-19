package io.natskt.client

import io.ktor.http.URLBuilder
import io.natskt.api.Credentials
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.NUID
import io.natskt.internal.OperationSerializerImpl
import io.natskt.internal.connectionCoroutineDispatcher
import io.natskt.internal.platformDefaultTransport
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

public class ClientConfigurationBuilder internal constructor() {
	public var server: String? = null
	public var servers: Collection<String>? = null
	public var authentication: Credentials? = null
	public var inboxPrefix: String = "_INBOX."
	public var maxReconnects: Int? = null
	public var maxControlLineBytes: Int = 1024
	public var connectTimeoutMs: Long = 5000
	public var reconnectDebounceMs: Long = 2000
	public var tlsRequired: Boolean = false
	public var transport: TransportFactory? = null
	public var scope: CoroutineScope? = null
}

internal fun ClientConfigurationBuilder.build(): ClientConfiguration {
	val serversList =
		buildList {
			servers?.forEach {
				add(parseUrl(it))
			}
			server?.let { add(parseUrl(it)) }
		}.also {
			if (it.isEmpty()) error("must provide at least one server")
		}

	val inboxPrefix = if (inboxPrefix.endsWith(".")) inboxPrefix else "$inboxPrefix."

	return ClientConfiguration(
		servers = serversList,
		transportFactory = transport ?: platformDefaultTransport,
		credentials = authentication,
		inboxPrefix = inboxPrefix,
		parser = OperationSerializerImpl(),
		maxReconnects = maxReconnects,
		connectTimeoutMs = connectTimeoutMs,
		reconnectDebounceMs = reconnectDebounceMs,
		maxControlLineBytes = maxControlLineBytes,
		tlsRequired = tlsRequired,
		nuid = NUID.Default,
		scope = scope ?: CoroutineScope(connectionCoroutineDispatcher + SupervisorJob() + CoroutineName("NatsClient")),
	)
}

private fun parseUrl(raw: String): NatsServerAddress =
	NatsServerAddress(
		URLBuilder(raw)
			.build(),
	)

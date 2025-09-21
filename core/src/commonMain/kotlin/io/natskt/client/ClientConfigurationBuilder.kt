package io.natskt.client

import io.ktor.http.URLBuilder
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.NUID
import io.natskt.internal.OperationSerializerImpl
import io.natskt.internal.platformDefaultTransport
import kotlinx.coroutines.CoroutineScope

internal interface ClientConfigurationValues {
	val servers: Collection<String>?
	val inboxPrefix: String
	val maxReconnects: Int?
	val maxControlLineBytes: Int?
	val connectTimeoutMs: Long?
	val reconnectDebounceMs: Long?
	val transport: TransportFactory?
	val scope: CoroutineScope?
}

public class ClientConfigurationBuilder : ClientConfigurationValues {
	public var server: String? = null
	public override var servers: Collection<String>? = null
	public override var inboxPrefix: String = "_INBOX."
	public override var maxReconnects: Int? = null
	public override var maxControlLineBytes: Int = 1024
	public override var connectTimeoutMs: Long = 5000
	public override var reconnectDebounceMs: Long = 2000
	public override var transport: TransportFactory? = null
	public override var scope: CoroutineScope? = null
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
	return ClientConfiguration(
		servers = serversList,
		transportFactory = transport ?: platformDefaultTransport,
		inboxPrefix = inboxPrefix,
		parser = OperationSerializerImpl(),
		maxReconnects = maxReconnects,
		connectTimeoutMs = connectTimeoutMs,
		reconnectDebounceMs = reconnectDebounceMs,
		maxControlLineBytes = maxControlLineBytes,
		nuid = NUID.Default,
		scope = scope,
	)
}

private fun parseUrl(raw: String): NatsServerAddress =
	NatsServerAddress(
		URLBuilder(raw)
			.build(),
	)

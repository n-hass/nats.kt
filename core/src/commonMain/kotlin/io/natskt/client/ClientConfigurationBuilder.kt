package io.natskt.client

import io.ktor.http.URLBuilder
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.NUID
import io.natskt.internal.OperationSerializerImpl
import io.natskt.internal.platformDefaultTransport
import kotlinx.coroutines.CoroutineScope

internal interface ClientConfigurationValues {
    val servers: Collection<String>?
    val maxReconnects: Int?
    val connectTimeoutMs: Int?
    val transport: TransportFactory?
    val scope: CoroutineScope?
}

public class ClientConfigurationBuilder : ClientConfigurationValues {
    public var server: String? = null
    public override var servers: Collection<String>? = null
    public override var maxReconnects: Int? = null
    public override var connectTimeoutMs: Int? = null
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
        inboxPrefix = "_INBOX.",
        parser = OperationSerializerImpl(),
        maxReconnects = maxReconnects,
        connectTimeoutMs = 5000,
        nuid = NUID.Default,
        scope = scope,
    )
}

private fun parseUrl(raw: String): NatsServerAddress =
    NatsServerAddress(
        URLBuilder(raw)
            .build(),
    )

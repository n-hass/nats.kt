package io.natskt.client

import io.ktor.http.Url
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.NUID
import io.natskt.internal.OperationParserImpl
import io.natskt.internal.api.OperationParser
import io.natskt.internal.platformDefaultTransport

internal interface ClientConfigurationValues {
    val server: String?
    val connectTimeoutMs: Int?
    val transport: TransportFactory?
}

public class ClientConfigurationBuilder(
    public override var server: String? = null,
) : ClientConfigurationValues {
    public override var connectTimeoutMs: Int? = null
    public override var transport: TransportFactory? = null

    public fun build(): ClientConfiguration =
        ClientConfiguration(
            servers = server?.let { listOf(NatsServerAddress(Url(it))) } ?: error("must give url"),
            transportFactory = transport ?: platformDefaultTransport,
            inboxPrefix = "_INBOX.",
            parser = OperationParserImpl(),
            maxReconnects = -1,
            connectTimeoutMs = 5000,
            nuid = NUID.Default,
        )
}

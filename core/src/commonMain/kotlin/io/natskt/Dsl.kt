package io.natskt

import io.natskt.api.NatsClient
import io.natskt.client.ClientConfiguration
import io.natskt.client.ClientConfigurationBuilder
import io.natskt.client.NatsClientImpl
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public fun NatsClient(configuration: ClientConfiguration): NatsClient = NatsClientImpl(configuration)

@OptIn(ExperimentalContracts::class)
public inline fun NatsClient(
    uri: String,
    block: ClientConfigurationBuilder.() -> Unit,
): NatsClient {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val config =
        ClientConfigurationBuilder(uri)
            .apply(block)
            .build()

    val client = NatsClient(config)

    return client
}

@OptIn(ExperimentalContracts::class)
public fun NatsClient(uri: String): NatsClient {
    val config =
        ClientConfigurationBuilder(uri)
            .build()

    val client = NatsClient(config)

    return client
}

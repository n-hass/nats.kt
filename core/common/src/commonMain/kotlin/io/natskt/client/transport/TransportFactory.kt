package io.natskt.client.transport

import io.natskt.client.NatsServerAddress
import kotlin.coroutines.CoroutineContext

public interface TransportFactory {
    public suspend fun connect(
        address: NatsServerAddress,
        context: CoroutineContext,
    ): Transport
}

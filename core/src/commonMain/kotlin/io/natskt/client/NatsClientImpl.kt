package io.natskt.client

import io.natskt.api.Connection
import io.natskt.api.InternalNatsApi
import io.natskt.api.NatsClient
import io.natskt.api.Subscription
import io.natskt.internal.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

internal class NatsClientImpl(
    override val configuration: ClientConfiguration,
) : NatsClient {
    internal val conn = ConnectionImpl(configuration)

    @InternalNatsApi
    override val connection: Connection
        get() = conn
    override val subscriptions: Map<String, Subscription>
        get() = TODO("Not yet implemented")

    override suspend fun connect() {
        conn.connect()
        CoroutineScope(currentCoroutineContext()).launch {
            conn.incoming.collect {
//                println("came through: $it")
            }
        }
    }

    override suspend fun subscribe(
        subject: Subject,
        queueGroup: String?,
    ): Subscription {
        TODO("Not yet implemented")
    }

    override suspend fun subscribe(
        id: String,
        subject: Subject,
        queueGroup: String?,
    ): Subscription {
        TODO("Not yet implemented")
    }
}

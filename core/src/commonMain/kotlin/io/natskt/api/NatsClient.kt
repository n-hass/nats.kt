package io.natskt.api

import io.natskt.client.ClientConfiguration
import io.natskt.internal.Subject

public interface NatsClient {
    @InternalNatsApi
    public val connection: Connection

    /**
     *
     */
    public val configuration: ClientConfiguration

    /**
     * The subscriptions that have been created with this [NatsClient].
     */
    public val subscriptions: Map<String, Subscription>

    /**
     *
     */
    public suspend fun connect()

    /**
     *
     */
    public suspend fun subscribe(
        subject: Subject,
        queueGroup: String? = null,
    ): Subscription

    /**
     *
     */
    public suspend fun subscribe(
        id: String,
        subject: Subject,
        queueGroup: String? = null,
    ): Subscription
}

package io.natskt.client

import io.natskt.api.internal.OperationSerializer
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.NUID
import io.natskt.internal.Subject
import kotlinx.coroutines.CoroutineScope

internal data class ClientConfiguration(
    /**
     * Servers to possibly connect to.
     */
    val servers: List<NatsServerAddress>,
    /**
     * either a []
     */
    val transportFactory: TransportFactory,
    /**
     *
     */
    val inboxPrefix: String,
    /**
     *
     */
    internal val parser: OperationSerializer,
    /**
     * The max number of reconnects for a single server.
     */
    val maxReconnects: Int?,
    val connectTimeoutMs: Int = 5000,
    /**
     * The NUID generator to use.
     */
    val nuid: NUID,
    /**
     * The coroutine scope that the client's connections and parsing runs on
     */
    val scope: CoroutineScope?,
) {
    /**
     * The length of an inbox created using [createInbox]
     */
    val inboxLength: Int get() = inboxPrefix.length + 22

    /**
     *
     */
    public fun createInbox(): Subject = Subject(inboxPrefix + nuid.next())
}

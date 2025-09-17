package io.natskt.client

import io.natskt.client.transport.TransportFactory
import io.natskt.internal.NUID
import io.natskt.internal.Subject
import io.natskt.internal.api.OperationParser

public data class ClientConfiguration(
    /**
     * Servers to possibly connect to.
     */
    val servers: List<NatsServerAddress>,
    /**
     *
     */
    val transportFactory: TransportFactory,
    /**
     *
     */
    val inboxPrefix: String,
    /**
     *
     */
    val parser: OperationParser,
    /**
     * The max number of reconnects for a single server.
     */
    val maxReconnects: Int?,
    val connectTimeoutMs: Int = 5000,
    /**
     * The NUID generator to use.
     */
    val nuid: NUID,
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

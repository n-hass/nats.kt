package io.natskt.internal

import io.ktor.client.engine.cio.CIO
import io.natskt.client.transport.TransportFactory
import io.natskt.client.transport.WebSocketTransport

internal actual val platformDefaultTransport: TransportFactory = WebSocketTransport.Factory(CIO)

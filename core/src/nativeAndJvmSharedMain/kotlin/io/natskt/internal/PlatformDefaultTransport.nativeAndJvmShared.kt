package io.natskt.internal

import io.natskt.client.transport.TcpTransport
import io.natskt.client.transport.TransportFactory

internal actual val platformDefaultTransport: TransportFactory = TcpTransport

package io.natskt.client.transport

import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.connection
import io.ktor.network.tls.tls
import kotlin.coroutines.coroutineContext

internal suspend fun upgradeTlsNative(connection: Connection): TcpTransport = TcpTransport(connection.tls(coroutineContext).connection())

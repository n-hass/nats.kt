package io.natskt

import io.ktor.client.engine.cio.CIO
import io.natskt.client.transport.TcpTransport
import io.natskt.client.transport.WebSocketTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.minutes

fun main(): Unit = runBlocking {
    NatsClient("nats://localhost:4222") {
        transport = TcpTransport
    }
    .connect()
//    NatsClient("nats://localhost:8888") {
//        transport = WebSocketTransport.Factory(CIO)
//    }
//    .connect()

    delay(10.minutes)
}
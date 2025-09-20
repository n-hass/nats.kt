package io.natskt

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.Url
import io.ktor.utils.io.readUTF8Line
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.natskt.client.NatsServerAddress
import io.natskt.client.transport.TcpTransport
import io.natskt.client.transport.WebSocketTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.minutes

fun main(): Unit = runBlocking {
    NatsClient {
        server = "nats://localhost:4222"
        transport = TcpTransport
    }
    .connect()
//    NatsClient {
//        server = "ws://localhost:8888"
//        transport = WebSocketTransport.Factory(CIO)
//		maxReconnects = 4
//    }
//    .connect()

    delay(10.minutes)
}
@file:OptIn(ExperimentalTime::class)

package io.natskt

import io.natskt.client.transport.TcpTransport
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun main(): Unit = runBlocking {
    val c = NatsClient {
        server = "nats://localhost:4222"
		transport = TcpTransport
//        server = "ws://localhost:8888"
//        transport = WebSocketTransport.Factory(io.ktor.client.engine.okhttp.OkHttp)
		inboxPrefix = "_INBOX.me."
    }.also {
		it.connect()
	}

	val runs = 100_000

	val start = Clock.System.now()

	for (i in 1..runs) {
		c.publish("test.publish", "HI FROM KOTLIN $i".toByteArray())
	}

	val stop = Clock.System.now()

	println("final time: ${stop - start}")
	System.exit(0)
}
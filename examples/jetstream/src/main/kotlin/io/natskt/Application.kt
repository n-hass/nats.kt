package io.natskt

import io.natskt.client.transport.TcpTransport
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val c = NatsClient {
        server = "nats://localhost:4222"
        transport = TcpTransport
		inboxPrefix = "_INBOX.me."
    }.also {
		it.connect()
	}


}
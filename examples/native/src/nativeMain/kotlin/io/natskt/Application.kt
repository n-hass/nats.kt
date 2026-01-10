package io.natskt

import io.ktor.utils.io.core.toByteArray
import io.natskt.client.transport.TcpTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {

    val c = NatsClient {
        server = "nats://localhost:4222"
        transport = TcpTransport
		inboxPrefix = "_INBOX.me."
    }.also {
		it.connect()
	}

	for (i in 1..50000) {
		launch {
			val response = c.request("test.service.echo", "HI FROM KOTLIN $i".toByteArray())
			println("-------- $i: ${response.data?.decodeToString()}")
		}
	}

	do {
		delay(1000)
		println("size is: ${c.subscriptions.size}")
	} while(c.subscriptions.isNotEmpty())

	println("complete")
}

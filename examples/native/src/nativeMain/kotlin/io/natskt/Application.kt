package io.natskt

import io.ktor.utils.io.core.toByteArray
import io.natskt.client.transport.TcpTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {

	delay(5000)
	println("starting in")
	println("3")
	delay(1000)
	println("2")
	delay(1000)
	println("1")
	delay(1000)
    val c = NatsClient {
        server = "nats://localhost:4222"
        transport = TcpTransport
		inboxPrefix = "_INBOX.me."
    }.also {
		it.connect()
	}

	for (i in 1..5000) {
		launch {
			c.request("test.service.echo", "HI FROM KOTLIN $i".toByteArray()).await().also {
				println("-------- $i: ${it.data?.decodeToString()}")
			}
		}
	}

	do {
		delay(1000)
		println("size is: ${c.subscriptions.size}")
	} while(c.subscriptions.isNotEmpty())

	println("complete")
}
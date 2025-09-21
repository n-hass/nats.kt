package io.natskt

import io.natskt.client.transport.TcpTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.minutes

fun main(): Unit = runBlocking {
    val c = NatsClient {
        server = "nats://localhost:4222"
        transport = TcpTransport
		inboxPrefix = "_INBOX.me."
    }.also {
		it.connect()
	}

	for (i in 1..20000) {
		launch {
			c.request("test.service.echo", "HI FROM KOTLIN $i".toByteArray()).await().also {
				println("-------- $i: ${it.data?.decodeToString()}")
			}
		}
	}

	delay(20000)

	println("doing GC")
	System.gc()

	delay(20.minutes)

	println("finished collecting")
}
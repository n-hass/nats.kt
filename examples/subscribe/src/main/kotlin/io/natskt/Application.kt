package io.natskt

import io.natskt.client.transport.TcpTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

fun main(): Unit = runBlocking {
    val c = NatsClient {
        server = "nats://localhost:4222"
        transport = TcpTransport
    }.also {
		it.connect()
	}

//	val c = NatsClient {
//		server = "ws://localhost:8888"
//		transport = WebSocketTransport.Factory(CIO)
//		maxReconnects = 4
//	}.also {
//		it.connect()
//	}

	c.publish("test.out1", "something".toByteArray())

	val sub = c.subscribe("test.echo")

	withTimeoutOrNull(10_000) {
		launch {
			sub.messages.collect {
				println("got: ${it.data?.decodeToString()}")
			}
		}
	}

	// subscription was automatically closed when the last collector stopped

    delay(10.minutes)
}
package io.natskt

import io.natskt.client.transport.TcpTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

	val subscription = c.subscribe("test.hi", eager = false)

	launch {
		delay(20_000)
		subscription.unsubscribe()
	}

	subscription.messages.collect {
		println("got a message: ${it.data?.decodeToString()}")
	}

	println("finished collecting")
}
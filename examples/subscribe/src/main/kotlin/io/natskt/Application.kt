package io.natskt

import io.natskt.client.transport.TcpTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

	delay(5000)
	val subscription = c.subscribe("test.hi", eager = false)


	println("starting in 3")
	delay(1000)
	println("2")
	delay(1000)
	println("1")
	delay(1000)

	launch {
		delay(20_000)
		subscription.unsubscribe()
	}

	val jobs = mutableListOf<Job>()
	jobs.add(launch {
		subscription.messages.collect {
			println("got a message: ${it.data?.decodeToString()}")
		}
	})
	delay(2000)
	jobs.add(launch {
		subscription.messages.collect {
			println("got a message 2: ${it.data?.decodeToString()}")
		}
	})

	delay(2000)
	jobs.add(launch {
		subscription.messages.collect {
			println("got a message 3: ${it.data?.decodeToString()}")
		}
	})

	jobs.joinAll()

	println("finished collecting")

	delay(10.minutes)
}
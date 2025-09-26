@file:OptIn(ExperimentalTime::class)

package io.natskt

import io.natskt.client.transport.TcpTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

fun main(): Unit = runBlocking {
    val c = NatsClient {
        server = "nats://localhost:4222"
        transport = TcpTransport
		inboxPrefix = "_INBOX.me."
    }.also {
		it.connect()
	}

	val sub = c.subscribe("test.sub")

	launch {
		delay(30.seconds)
		sub.unsubscribe()
	}

	sub.messages.collect {
		println(it.data?.decodeToString())
	}

	System.exit(0)
}
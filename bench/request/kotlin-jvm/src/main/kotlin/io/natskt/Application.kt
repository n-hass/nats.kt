@file:OptIn(ExperimentalTime::class)

package io.natskt

import io.natskt.client.transport.TcpTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun main(): Unit = runBlocking {
    val c = NatsClient {
        server = "nats://localhost:4222"
        transport = TcpTransport
		inboxPrefix = "_INBOX.me."
    }.also {
		it.connect()
	}

	val runs = 100_000

	val default = launch {  }
	val jobs: MutableList<Job> = MutableList(runs) { default }

	val start = Clock.System.now()
	for (i in 1..runs) {
		jobs.add(launch {
			c.request("test.service.echo", "HI FROM KOTLIN $i".toByteArray()).await()
		})
	}

	jobs.joinAll()
	val stop = Clock.System.now()

	println("final time: ${stop - start}")
//	delay(10.minutes)
}
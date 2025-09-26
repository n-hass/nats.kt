@file:OptIn(ExperimentalTime::class)

package io.natskt

import io.nats.client.Nats
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun main(): Unit = runBlocking {
	val c = Nats.connect("nats://localhost:4222")

	val runs = 100_000

	val start = Clock.System.now()
	for (i in 1..runs) {
		c.publish("test.service.echo", "HI FROM JAVA $i".toByteArray())
	}

	val stop = Clock.System.now()

	println("final time: ${stop - start}")
	System.exit(0)
}
@file:OptIn(ExperimentalTime::class)

package io.natskt

import io.nats.client.Nats
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

fun main(): Unit = runBlocking {
	val c = Nats.connect("nats://localhost:4222")

	val d = c.createDispatcher {
		println(it.data.decodeToString())
	}

	d.subscribe("test.sub")

	delay(30.seconds)

	System.exit(0)
}
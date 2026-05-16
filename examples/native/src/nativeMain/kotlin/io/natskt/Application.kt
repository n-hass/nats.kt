package io.natskt

import io.ktor.utils.io.core.toByteArray
import io.natskt.client.transport.TcpTransport
import io.natskt.api.Credentials
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.Level
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration

fun main(): Unit = runBlocking {
	KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
	KotlinLoggingConfiguration.direct.logLevel = Level.Debug

    val c = NatsClient {
        server = "nats://localhost:4222"
        transport = TcpTransport
		inboxPrefix = "_INBOX.me."
    }

	c.connect().getOrThrow()

	val response = c.subscribe("test.hi").messages.collect {
		println("got: ${it.data?.decodeToString()}")
	}

	println("complete")
}

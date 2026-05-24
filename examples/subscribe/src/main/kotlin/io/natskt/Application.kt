package io.natskt

import io.natskt.api.Credentials
import io.natskt.client.transport.TcpTransport
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.Level
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration

fun main(): Unit = runBlocking {
	KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
	KotlinLoggingConfiguration.direct.logLevel = Level.TRACE
	val credsPath = System.getenv("NATS_CREDS").takeIf { it.isNotBlank() }
	val server = System.getenv("NATS_SERVER").takeIf { it.isNotBlank() } ?: "nats://localhost"

    val c = NatsClient {
		this.server = server
        transport = TcpTransport
		if (credsPath != null) {
			authentication = Credentials.File(
				File(credsPath).readText()
			)
		}
    }.also {
		it.connect()
	}

	val subscription = c.subscribe("test.hi", eager = false)

	subscription.messages.collect {
		println("got a message: ${it.data?.decodeToString()}")
	}

	println("finished collecting")
}
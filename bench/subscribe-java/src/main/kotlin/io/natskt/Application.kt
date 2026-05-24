package io.natskt

import io.nats.client.Nats
import io.nats.client.Options
import java.time.Duration
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

fun main() {
    val credsPath = System.getenv("NATS_CREDS").takeIf { it.isNotBlank() }
	val server = System.getenv("NATS_SERVER").takeIf { it.isNotBlank() } ?: "nats://localhost"

    val options = Options.builder().apply {
        server(server)
		if (credsPath != null) {
			authHandler(Nats.credentials(credsPath))
		}
        connectionTimeout(Duration.ofSeconds(5))
		connectionListener { connection, events ->
			println("event: $events")
		}
	}.build()

    Nats.connect(options).use { nc ->
        val done = CountDownLatch(1)

        val dispatcher = nc.createDispatcher { msg ->
            println("got a message: ${msg.data?.toString(Charsets.UTF_8).orEmpty()}")
        }
        dispatcher.subscribe("test.hi")

        Runtime.getRuntime().addShutdownHook(Thread { done.countDown() })
        done.await()

        println("finished collecting")
    }
}

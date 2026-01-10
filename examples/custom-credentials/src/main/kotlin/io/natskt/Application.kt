package io.natskt

import io.natskt.api.AuthPayload
import io.natskt.api.AuthProvider
import io.natskt.api.Credentials
import io.natskt.client.transport.TcpTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val c = NatsClient {
        server = "nats://localhost:4222"
        transport = TcpTransport
		maxReconnects = 3
		authentication = Credentials.Custom(
			AuthProvider { info ->
				AuthPayload(
					jwt = "ey8786",
					nkey = "customkey",
					password = "123"
				)
			}
		)
    }

	c.connect()

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
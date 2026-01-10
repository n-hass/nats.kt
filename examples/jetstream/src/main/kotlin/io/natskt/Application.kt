package io.natskt

import io.natskt.client.transport.TcpTransport
import io.natskt.jetstream.JetStreamClient
import io.natskt.jetstream.api.consumer.PushConsumer
import io.natskt.jetstream.api.consumer.SubscribeOptions
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val c = NatsClient {
        server = "nats://localhost:4222"
        transport = TcpTransport
		inboxPrefix = "_INBOX.me."
    }.also {
		it.connect()
	}

	val js = JetStreamClient(c)

	js.manager.createStream {
		name = "my_stream"
		subjects = mutableListOf(
			"abc.123.>"
		)
	}

	val info = js.manager.createOrUpdateConsumer("my_stream") {
		deliverSubject = "consumer.a"
	}

	val consumer = js.subscribe(SubscribeOptions.Attach("my_stream", info.name))

	if (consumer !is PushConsumer) {
		throw IllegalStateException()
	}

	consumer.messages.collect {
		println("message: ${it.data?.decodeToString()}")
	}
}
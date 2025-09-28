package io.natskt.jetstream.client

import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.client.ByteMessageBuilder
import io.natskt.client.StringMessageBuilder
import io.natskt.jetstream.api.JetStreamClient

internal class JetStreamClientImpl(
	private val client: NatsClient,
	private val config: JetStreamConfiguration,
) : JetStreamClient {
	override suspend fun publish(
		subject: String,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: String?,
	) {
		TODO("Not yet implemented")
	}

	override suspend fun publish(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: Subject?,
	) {
		TODO("Not yet implemented")
	}

	override suspend fun publish(message: Message) {
		TODO("Not yet implemented")
	}

	override suspend fun publishBytes(byteMessageBlock: ByteMessageBuilder.() -> Unit) {
		TODO("Not yet implemented")
	}

	override suspend fun publishString(stringMessageBlock: StringMessageBuilder.() -> Unit) {
		TODO("Not yet implemented")
	}
}

package io.natskt.jetstream.client

import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.client.ByteMessageBuilder
import io.natskt.client.StringMessageBuilder
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.PublishAck
import kotlinx.coroutines.Deferred

internal class JetStreamClientImpl(
	private val client: NatsClient,
	private val config: JetStreamConfiguration,
) : JetStreamClient {
	override suspend fun publish(
		subject: String,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: String?,
	): Deferred<PublishAck> {
		TODO("Not yet implemented")
	}

	override suspend fun publish(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: Subject?,
	): Deferred<PublishAck> {
		TODO("Not yet implemented")
	}

	override suspend fun publish(message: Message): Deferred<PublishAck> {
		TODO("Not yet implemented")
	}

	override suspend fun publishBytes(byteMessageBlock: ByteMessageBuilder.() -> Unit): Deferred<PublishAck> {
		TODO("Not yet implemented")
	}

	override suspend fun publishString(stringMessageBlock: StringMessageBuilder.() -> Unit): Deferred<PublishAck> {
		TODO("Not yet implemented")
	}
}

@file:OptIn(InternalNatsApi::class)

package io.natskt.jetstream.client

import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.api.internal.InternalNatsApi
import io.natskt.client.ByteMessageBuilder
import io.natskt.client.StringMessageBuilder
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.PublishAck

internal class JetStreamClientImpl(
	private val client: NatsClient,
	private val config: JetStreamConfiguration,
) : JetStreamClient {
	override suspend fun publish(
		subject: String,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: String?,
	): PublishAck {
		TODO("Not yet implemented")
	}

	override suspend fun publish(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: Subject?,
	): PublishAck {
		TODO("Not yet implemented")
	}

	override suspend fun publish(message: Message): PublishAck {
		TODO("Not yet implemented")
	}

	override suspend fun publishBytes(byteMessageBlock: ByteMessageBuilder.() -> Unit): PublishAck {
		TODO("Not yet implemented")
	}

	override suspend fun publishString(stringMessageBlock: StringMessageBuilder.() -> Unit): PublishAck {
		TODO("Not yet implemented")
	}
}

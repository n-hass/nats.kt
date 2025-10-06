package io.natskt.jetstream.api

import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.client.ByteMessageBuilder
import io.natskt.client.StringMessageBuilder
import io.natskt.jetstream.client.JetStreamClientImpl
import io.natskt.jetstream.client.JetStreamConfiguration

public interface JetStreamClient {
	public suspend fun publish(
		subject: String,
		message: ByteArray,
		headers: Map<String, List<String>>? = null,
		replyTo: String? = null,
	): PublishAck

	public suspend fun publish(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>? = null,
		replyTo: Subject? = null,
	): PublishAck

	public suspend fun publish(message: Message): PublishAck

	public suspend fun publishBytes(byteMessageBlock: ByteMessageBuilder.() -> Unit): PublishAck

	public suspend fun publishString(stringMessageBlock: StringMessageBuilder.() -> Unit): PublishAck

	public companion object {
		internal operator fun invoke(
			client: NatsClient,
			config: JetStreamConfiguration,
		) = JetStreamClientImpl(client, config)
	}
}

package io.natskt.jetstream.api

import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.client.ByteMessageBuilder
import io.natskt.client.StringMessageBuilder
import io.natskt.jetstream.api.consumer.PullConsumer
import io.natskt.jetstream.api.stream.Stream
import io.natskt.jetstream.client.JetStreamClientImpl
import io.natskt.jetstream.client.JetStreamConfiguration
import io.natskt.jetstream.internal.CanRequest

public interface JetStreamClient : CanRequest {
	public val client: NatsClient

	public val manager: JetStreamManager
	public val keyValueManager: KeyValueManager

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

	/**
	 * Bind to an existing pull consumer by name
	 */
	public suspend fun pull(
		streamName: String,
		consumerName: String,
	): PullConsumer

	/**
	 * Return an existing [io.natskt.jetstream.api.stream.Stream], fetching its [StreamInfo]
	 */
	public suspend fun stream(name: String): Stream

	public companion object {
		internal operator fun invoke(
			client: NatsClient,
			config: JetStreamConfiguration,
		) = JetStreamClientImpl(client, config)
	}
}

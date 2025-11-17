package io.natskt.jetstream.api

import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.jetstream.api.consumer.Consumer
import io.natskt.jetstream.api.kv.KeyValueBucket
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
		headers: Map<String, List<String>>?,
		replyTo: String?,
		publishOptions: PublishOptions,
	): PublishAck

	public suspend fun publish(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>? = null,
		replyTo: Subject? = null,
		publishOptions: PublishOptions,
	): PublishAck

	public suspend fun publish(
		message: Message,
		publishOptions: PublishOptions,
	): PublishAck

	/**
	 * Create a [Consumer] instance to read messages from a stream.
	 * You can attach to an existing consumer with [SubscribeOptions.Attach],
	 * or upsert one with [SubscribeOptions.CreateOrUpdate]
	 */
	public suspend fun subscribe(
		subject: String,
		subscribeOptions: SubscribeOptions,
	): Consumer

	/**
	 * Return an existing [io.natskt.jetstream.api.stream.Stream], fetching its [StreamInfo]
	 */
	public suspend fun stream(name: String): Stream

	/**
	 * Create a key-value bucket binding.
	 * **Creates a request subscription** that must be closed when you are finished,
	 * or you can wrap its usage with [AutoCloseable.use]
	 */
	public suspend fun keyValue(bucket: String): KeyValueBucket

	public companion object {
		internal operator fun invoke(
			client: NatsClient,
			config: JetStreamConfiguration,
		) = JetStreamClientImpl(client, config)
	}
}

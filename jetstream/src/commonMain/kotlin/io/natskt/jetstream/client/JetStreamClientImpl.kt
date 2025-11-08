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
import io.natskt.jetstream.api.consumer.PullConsumer
import io.natskt.jetstream.api.stream.Stream
import io.natskt.jetstream.internal.PersistentRequestSubscription
import io.natskt.jetstream.internal.StreamImpl
import io.natskt.jetstream.management.JetStreamManagementImpl

internal class JetStreamClientImpl(
	override val client: NatsClient,
	override val config: JetStreamConfiguration,
) : JetStreamClient {
	var management: JetStreamManagementImpl? = null

	override suspend fun management(): JetStreamManagementImpl {
		if (management == null) {
			val new = JetStreamManagementImpl(this, PersistentRequestSubscription.newSubscription(client))
			management = new
			return new
		}
		return management!!
	}

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
	): PublishAck = publish(subject.raw, message, headers, replyTo?.raw)

	override suspend fun publish(message: Message): PublishAck {
		require(message.replyTo == null) { "JetStream publish does not support custom reply subjects" }

		return publish(
			message.subject.raw,
			message.data ?: ByteArray(0),
			message.headers,
			null,
		)
	}

	override suspend fun publishBytes(byteMessageBlock: ByteMessageBuilder.() -> Unit): PublishAck {
		val builder = ByteMessageBuilder().apply(byteMessageBlock)
		val subject = builder.subject ?: error("subject must be set")
		require(builder.replyTo == null) { "JetStream publish does not support custom reply subjects" }

		return publish(
			subject,
			builder.data ?: ByteArray(0),
			builder.headers,
			null,
		)
	}

	override suspend fun publishString(stringMessageBlock: StringMessageBuilder.() -> Unit): PublishAck {
		val builder = StringMessageBuilder().apply(stringMessageBlock)
		val subject = builder.subject ?: error("subject must be set")
		require(builder.replyTo == null) { "JetStream publish does not support custom reply subjects" }

		return publish(
			subject,
			builder.data?.encodeToByteArray() ?: ByteArray(0),
			builder.headers,
			null,
		)
	}

	override suspend fun pull(
		streamName: String,
		consumerName: String,
	): PullConsumer {
		TODO("Not yet implemented")
	}

	override suspend fun stream(name: String): Stream =
		StreamImpl(
			name,
			this,
			null,
		).also { it.updateStreamInfo() }

	override suspend fun request(
		subject: String,
		message: String?,
		headers: Map<String, List<String>>?,
		timeoutMs: Long,
	): Message = client.request(subject, message?.encodeToByteArray(), headers, timeoutMs)
}

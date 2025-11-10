@file:OptIn(InternalNatsApi::class)

package io.natskt.jetstream.client

import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.api.internal.InternalNatsApi
import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.JetStreamManager
import io.natskt.jetstream.api.KeyValueManager
import io.natskt.jetstream.api.PublishAck
import io.natskt.jetstream.api.PublishOptions
import io.natskt.jetstream.api.consumer.PullConsumer
import io.natskt.jetstream.api.internal.decode
import io.natskt.jetstream.api.kv.KeyValueBucket
import io.natskt.jetstream.api.stream.Stream
import io.natskt.jetstream.internal.JetStreamContext
import io.natskt.jetstream.internal.KeyValueManagerImpl
import io.natskt.jetstream.internal.PersistentRequestSubscription
import io.natskt.jetstream.internal.StreamImpl
import io.natskt.jetstream.management.JetStreamManagerImpl
import kotlinx.coroutines.delay
import kotlin.collections.buildMap

internal class JetStreamClientImpl(
	override val client: NatsClient,
	config: JetStreamConfiguration,
) : JetStreamClient {
	private val baseRequest = client.nextInbox() + "."

	override val context = JetStreamContext(config)

	override val manager: JetStreamManager by lazy {
		JetStreamManagerImpl(this)
	}

	override val keyValueManager: KeyValueManager by lazy {
		KeyValueManagerImpl(this)
	}

	override suspend fun publish(
		subject: String,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: String?,
		publishOptions: PublishOptions,
	): PublishAck {
		val msgHeaders =
			buildMap<String, List<String>> {
				fun put(
					k: String,
					v: String,
				) {
					val list = ArrayList<String>(1)
					list.add(v)
					this@buildMap[k] = list
				}

				publishOptions.id?.let { put(MSG_ID_HEADER, it) }
				publishOptions.expectedLastId?.let { put(EXPECTED_LAST_MSG_ID_HEADER, it) }
				publishOptions.expectedStream?.let { put(EXPECTED_STREAM_HEADER, it) }
				publishOptions.expectedLastSequence?.let { put(EXPECTED_LAST_SEQUENCE_HEADER, it.toString()) }
				publishOptions.expectedLastSubjectSequence?.let { put(EXPECTED_LAST_SUBJECT_SEQUENCE_HEADER, it.toString()) }
				publishOptions.ttl?.let { put(MSG_TTL_HEADER, it.toString()) }
			}.let {
				if (headers != null) it + headers else it
			}

		var lastError: Exception? = null
		var apiError: ApiError? = null
		val retryAttempts = publishOptions.retryAttempts
		val retryWait = publishOptions.retryWait

		for (attempt in 0..retryAttempts) {
			try {
				val data = client.request(subject, message, msgHeaders).decode<PublishAck>()
				when (data) {
					is PublishAck -> return data
					is ApiError -> {
						apiError = data
						delay(retryWait)
					}
					else -> {
						delay(retryWait)
					}
				}
			} catch (e: Exception) {
				lastError = e
			}
		}

		if (apiError != null) {
			throw JetStreamApiException(apiError)
		} else {
			throw RuntimeException("Failed to publish message", lastError)
		}
	}

	override suspend fun publish(
		subject: Subject,
		message: ByteArray,
		headers: Map<String, List<String>>?,
		replyTo: Subject?,
		publishOptions: PublishOptions,
	): PublishAck = publish(subject.raw, message, headers, replyTo?.raw, publishOptions)

	override suspend fun publish(
		message: Message,
		publishOptions: PublishOptions,
	): PublishAck {
		require(message.replyTo == null) { "JetStream publish does not support custom reply subjects" }

		return publish(
			message.subject.raw,
			message.data ?: ByteArray(0),
			message.headers,
			null,
			publishOptions,
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

	override suspend fun keyValue(bucket: String): KeyValueBucket {
		val inboxSubscription = PersistentRequestSubscription.newSubscription(client)
		return KeyValueBucket(this, inboxSubscription, bucket, null, null)
	}

	override suspend fun request(
		subject: String,
		message: String?,
		headers: Map<String, List<String>>?,
		timeoutMs: Long,
	): Message = client.request(subject, message?.encodeToByteArray(), headers, timeoutMs)
}

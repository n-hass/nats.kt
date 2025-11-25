@file:OptIn(InternalNatsApi::class)

package io.natskt.jetstream.client

import io.natskt.api.Message
import io.natskt.api.NatsClient
import io.natskt.api.Subject
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.throwOnInvalidSubject
import io.natskt.internal.throwOnInvalidToken
import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.JetStreamException
import io.natskt.jetstream.api.JetStreamManager
import io.natskt.jetstream.api.PublishAck
import io.natskt.jetstream.api.PublishOptions
import io.natskt.jetstream.api.consumer.Consumer
import io.natskt.jetstream.api.consumer.SubscribeOptions
import io.natskt.jetstream.api.internal.decode
import io.natskt.jetstream.api.kv.KeyValueBucket
import io.natskt.jetstream.api.kv.KeyValueManager
import io.natskt.jetstream.api.stream.Stream
import io.natskt.jetstream.internal.JetStreamContext
import io.natskt.jetstream.internal.KeyValueManagerImpl
import io.natskt.jetstream.internal.PersistentRequestSubscription
import io.natskt.jetstream.internal.PullConsumerImpl
import io.natskt.jetstream.internal.PushConsumerImpl
import io.natskt.jetstream.internal.StreamImpl
import io.natskt.jetstream.internal.createOrUpdateConsumer
import io.natskt.jetstream.internal.getConsumerInfo
import io.natskt.jetstream.internal.isPush
import io.natskt.jetstream.management.JetStreamManagerImpl
import kotlinx.coroutines.delay

internal class JetStreamClientImpl(
	override val client: NatsClient,
	config: JetStreamConfiguration,
) : JetStreamClient {
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
				when (val data = client.request(subject, message, msgHeaders).decode<PublishAck>()) {
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
			throw JetStreamException("Failed to publish message", lastError)
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

	private suspend fun consumerFromInfo(consumerInfo: ConsumerInfo): Consumer {
		if (consumerInfo.isPush() && consumerInfo.config.deliverSubject != null) {
			return PushConsumerImpl(
				name = consumerInfo.name,
				streamName = consumerInfo.stream,
				subscription = PushConsumerImpl.newSubscription(client, consumerInfo.config.deliverSubject),
				js = this,
				initialInfo = consumerInfo,
			)
		}

		return PullConsumerImpl(
			name = consumerInfo.name,
			streamName = consumerInfo.stream,
			js = this,
			inboxSubscription = PersistentRequestSubscription.newSubscription(client),
			initialInfo = consumerInfo,
		)
	}

	override suspend fun subscribe(
		subject: String,
		subscribeOptions: SubscribeOptions,
	): Consumer {
		subject.throwOnInvalidSubject()
		subscribeOptions.streamName.throwOnInvalidToken()
		return when (subscribeOptions) {
			is SubscribeOptions.Attach -> {
				subscribe(subscribeOptions)
			}
			is SubscribeOptions.CreateOrUpdate -> {
				val config =
					if (subscribeOptions.config.filterSubject == null && subscribeOptions.config.filterSubjects == null) {
						subscribeOptions.config.copy(filterSubject = subject)
					} else {
						subscribeOptions.config
					}
				val consumerInfo = createOrUpdateConsumer(subscribeOptions.streamName, config).getOrThrow()
				consumerFromInfo(consumerInfo)
			}
		}
	}

	override suspend fun subscribe(subscribeOptions: SubscribeOptions.Attach): Consumer {
		subscribeOptions.streamName.throwOnInvalidToken()
		subscribeOptions.consumerName.throwOnInvalidToken()
		val consumerInfo = getConsumerInfo(subscribeOptions.streamName, subscribeOptions.consumerName).getOrThrow()
		return consumerFromInfo(consumerInfo)
	}

	override suspend fun stream(name: String): Stream =
		StreamImpl(
			name,
			this,
			null,
		).also { it.updateStreamInfo() }

	override suspend fun keyValue(bucket: String): KeyValueBucket = KeyValueBucket(this, bucket, null, null)

	override suspend fun request(
		subject: String,
		message: String?,
		headers: Map<String, List<String>>?,
		timeoutMs: Long,
	): Message = client.request(subject, message?.encodeToByteArray(), headers, timeoutMs)
}

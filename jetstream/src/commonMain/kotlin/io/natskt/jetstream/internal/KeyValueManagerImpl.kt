package io.natskt.jetstream.internal

import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.KeyValueManager
import io.natskt.jetstream.api.kv.KeyValueBucket
import io.natskt.jetstream.api.kv.KeyValueConfigurationBuilder
import io.natskt.jetstream.api.kv.build

internal class KeyValueManagerImpl(
	private val js: JetStreamClient,
) : KeyValueManager {
	override suspend fun create(configure: KeyValueConfigurationBuilder.() -> Unit): KeyValueBucket {
		val config = KeyValueConfigurationBuilder().apply(configure).build()

		val accountInfo = js.getAccountInfo().getOrThrow()

		val createdInfo = js.createStream(config.asStreamConfig(accountInfo.api?.level ?: 0)).getOrThrow()

		val inboxSubscription = PersistentRequestSubscription.newSubscription(js.client)
		return KeyValueBucket(js, inboxSubscription, config.bucket, createdInfo.asKeyValueStatus(), createdInfo.asKeyValueConfig())
	}

	override suspend fun get(bucket: String): KeyValueBucket {
		val status = js.getStreamInfo(KV_BUCKET_STREAM_NAME_PREFIX + bucket)

		val bucketStatus =
			status
				.map {
					it.asKeyValueStatus()
				}.getOrThrow()

		val bucketConfig = status.map { it.asKeyValueConfig() }.getOrThrow()

		val inboxSubscription = PersistentRequestSubscription.newSubscription(js.client)
		return KeyValueBucket(js, inboxSubscription, bucket, bucketStatus, bucketConfig)
	}
}

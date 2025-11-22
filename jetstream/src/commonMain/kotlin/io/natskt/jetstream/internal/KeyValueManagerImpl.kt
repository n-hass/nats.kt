package io.natskt.jetstream.internal

import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.kv.KeyValueBucket
import io.natskt.jetstream.api.kv.KeyValueConfigurationBuilder
import io.natskt.jetstream.api.kv.KeyValueManager
import io.natskt.jetstream.api.kv.build

internal class KeyValueManagerImpl(
	private val js: JetStreamClient,
) : KeyValueManager {
	override suspend fun create(configure: KeyValueConfigurationBuilder.() -> Unit): KeyValueBucket {
		val config = KeyValueConfigurationBuilder().apply(configure).build()

		val accountInfo = js.getAccountInfo().getOrThrow()

		val createdInfo = js.createStream(config.asStreamConfig(accountInfo.api?.level ?: 0)).getOrThrow()

		return KeyValueBucket(js, config.bucket, createdInfo.asKeyValueStatus(), createdInfo.asKeyValueConfig())
	}

	override suspend fun get(bucket: String): KeyValueBucket {
		val status = js.getStreamInfo(KV_BUCKET_STREAM_NAME_PREFIX + bucket)

		val bucketStatus =
			status
				.map {
					it.asKeyValueStatus()
				}.getOrThrow()

		val bucketConfig = status.map { it.asKeyValueConfig() }.getOrThrow()

		return KeyValueBucket(js, bucket, bucketStatus, bucketConfig)
	}
}

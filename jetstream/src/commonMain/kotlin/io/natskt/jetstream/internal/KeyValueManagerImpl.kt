package io.natskt.jetstream.internal

import io.natskt.internal.throwOnInvalidToken
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.KeyValueStatus
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

		return KeyValueBucketImpl(js, config.bucket, createdInfo.asKeyValueStatus(), createdInfo.asKeyValueConfig())
	}

	override suspend fun update(
		bucket: String,
		configure: KeyValueConfigurationBuilder.() -> Unit,
	): KeyValueStatus {
		bucket.throwOnInvalidToken()
		val config = KeyValueConfigurationBuilder().apply(configure).build()
		val accountInfo = js.getAccountInfo().getOrThrow()
		val updatedInfo = js.updateStream(config.asStreamConfig(accountInfo.api?.level ?: 0)).getOrThrow()
		return updatedInfo.asKeyValueStatus()
	}

	override suspend fun delete(bucket: String): Boolean = js.deleteStream(KV_BUCKET_STREAM_NAME_PREFIX + bucket).getOrThrow()

	override suspend fun get(bucket: String): KeyValueBucket {
		val status = js.getStreamInfo(KV_BUCKET_STREAM_NAME_PREFIX + bucket)

		val bucketStatus =
			status
				.map {
					it.asKeyValueStatus()
				}.getOrThrow()

		val bucketConfig = status.map { it.asKeyValueConfig() }.getOrThrow()

		return KeyValueBucketImpl(js, bucket, bucketStatus, bucketConfig)
	}

	override suspend fun getStatus(bucket: String): KeyValueStatus = js.getStreamInfo(KV_BUCKET_STREAM_NAME_PREFIX + bucket).getOrThrow().asKeyValueStatus()

	override suspend fun getStatuses(): List<KeyValueStatus> =
		js
			.getStreams(null)
			.getOrThrow()
			.filter { it.config.name.startsWith(KV_BUCKET_STREAM_NAME_PREFIX) }
			.map { it.asKeyValueStatus() }

	override suspend fun getBucketNames(): List<String> =
		js
			.getStreamNames(null)
			.getOrThrow()
			.filter { it.startsWith(KV_BUCKET_STREAM_NAME_PREFIX) }
			.map { it.removePrefix(KV_BUCKET_STREAM_NAME_PREFIX) }
}

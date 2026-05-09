package io.natskt.jetstream.internal

import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.os.ObjectStoreBucket
import io.natskt.jetstream.api.os.ObjectStoreConfigurationBuilder
import io.natskt.jetstream.api.os.ObjectStoreManager
import io.natskt.jetstream.api.os.build

internal class ObjectStoreManagerImpl(
	private val js: JetStreamClient,
) : ObjectStoreManager {
	override suspend fun create(configure: ObjectStoreConfigurationBuilder.() -> Unit): ObjectStoreBucket {
		val config = ObjectStoreConfigurationBuilder().apply(configure).build()

		val createdInfo = js.createStream(config.asStreamConfig()).getOrThrow()

		return ObjectStoreBucket(
			js = js,
			name = config.bucket,
			initialStatus = createdInfo.asObjectStoreStatus(),
			initialConfig = createdInfo.asObjectStoreConfig(),
		)
	}

	override suspend fun get(bucket: String): ObjectStoreBucket {
		val streamInfo = js.getStreamInfo(toObjectStoreStreamName(bucket)).getOrThrow()
		return ObjectStoreBucket(
			js = js,
			name = bucket,
			initialStatus = streamInfo.asObjectStoreStatus(),
			initialConfig = streamInfo.asObjectStoreConfig(),
		)
	}
}

package io.natskt.jetstream.internal

import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.os.ObjectStoreBucket
import io.natskt.jetstream.api.os.ObjectStoreConfigurationBuilder
import io.natskt.jetstream.api.os.ObjectStoreManager
import io.natskt.jetstream.api.os.ObjectStoreStatus
import io.natskt.jetstream.api.os.build

/** Subject filter that matches every Object Store stream's subject space. */
internal const val OBJ_LIST_SUBJECT_FILTER: String = OBJ_SUBJECT_PREFIX + ">"

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

	override suspend fun update(
		bucket: String,
		configure: ObjectStoreConfigurationBuilder.() -> Unit,
	): ObjectStoreBucket {
		if (bucket.isBlank()) error("bucket name must be set")

		val existing =
			js
				.getStreamInfo(toObjectStoreStreamName(bucket))
				.getOrThrow()
				.asObjectStoreConfig()

		val merged = ObjectStoreConfigurationBuilder(existing).apply(configure).build()

		val updatedInfo = js.updateStream(merged.asStreamConfig()).getOrThrow()

		return ObjectStoreBucket(
			js = js,
			name = merged.bucket,
			initialStatus = updatedInfo.asObjectStoreStatus(),
			initialConfig = updatedInfo.asObjectStoreConfig(),
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

	override suspend fun delete(bucket: String): Boolean = js.deleteStream(toObjectStoreStreamName(bucket)).getOrThrow()

	override suspend fun names(): List<String> =
		js
			.getStreamNames(OBJ_LIST_SUBJECT_FILTER)
			.getOrThrow()
			.filter { it.startsWith(OBJ_BUCKET_STREAM_NAME_PREFIX) }
			.map { it.removePrefix(OBJ_BUCKET_STREAM_NAME_PREFIX) }

	override suspend fun list(): List<ObjectStoreStatus> =
		js
			.getStreams(OBJ_LIST_SUBJECT_FILTER)
			.getOrThrow()
			.filter { it.config.name.startsWith(OBJ_BUCKET_STREAM_NAME_PREFIX) }
			.map { it.asObjectStoreStatus() }
}

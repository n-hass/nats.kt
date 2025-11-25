package io.natskt.jetstream.internal

import io.natskt.api.ProtocolException
import io.natskt.jetstream.api.DiscardPolicy
import io.natskt.jetstream.api.KeyValueStatus
import io.natskt.jetstream.api.StreamCompression
import io.natskt.jetstream.api.StreamConfig
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.StreamSource
import io.natskt.jetstream.api.SubjectTransform
import io.natskt.jetstream.api.kv.KeyValueConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

public const val KV_BUCKET_MAX_HISTORY_SIZE: UShort = 64u

internal const val KV_BUCKET_STREAM_NAME_PREFIX: String = "KV_"

internal fun StreamInfo.asKeyValueStatus(): KeyValueStatus =
	KeyValueStatus(
		values = state.messages,
		ttl = config.maxAge,
		backingStore = "JetStream",
		bytes = state.bytes,
		isCompressed = config.compression != null && config.compression == StreamCompression.None,
	)

internal fun StreamInfo.asKeyValueConfig(): KeyValueConfig =
	KeyValueConfig(
		bucket = config.name.removePrefix(KV_BUCKET_STREAM_NAME_PREFIX),
		description = config.description,
		maxValueSize = config.maxMessageSize,
		history = config.maxMessagesPerSubject?.toUShort() ?: 1u,
		ttl = config.maxAge,
		maxBytes = config.maxBytes,
		storage = config.storage,
		replicas = config.replicas,
		placement = config.placement,
		republish = config.republish,
		mirror = config.mirror,
		sources = config.sources,
		compression = config.compression,
		limitMarkerTtl = config.subjectDeleteMarkerTtl,
		metadata = config.metadata,
	)

internal fun KeyValueConfig.asStreamConfig(apiLevel: Int): StreamConfig {
	val history = (this.history ?: 1u).toLong()
	require(history <= KV_BUCKET_MAX_HISTORY_SIZE.toLong()) {
		"KV buckets currently only support a maximum history of $KV_BUCKET_MAX_HISTORY_SIZE"
	}

	val replicas = (replicas ?: 1).let { if (it == 0) 1 else it }

	val maxBytes = (maxBytes ?: -1L).let { if (it == 0L) -1L else it }
	val maxMsgSize = (maxValueSize ?: -1).let { if (it == 0) -1 else it }

	val duplicateWindow =
		if (ttl != null && ttl > Duration.ZERO && ttl < 2.minutes) ttl else 2.minutes

	val compression = compression ?: StreamCompression.S2

	var allowMsgTtl: Boolean? = null
	val subjectDeleteMarkerTtl = limitMarkerTtl
	if (limitMarkerTtl != null && limitMarkerTtl != Duration.ZERO) {
		if (apiLevel < 1) {
			throw ProtocolException("limit marker ttl not supported by server: API level $apiLevel")
		}
		allowMsgTtl = true
	}

	fun kvSubjectsOf(bucket: String): String = "\$KV.$bucket.>"

	val subjects = mutableListOf(kvSubjectsOf(bucket))

	// Mirror / Sources handling (parity with Go):
	// - If Mirror is set, ensure the stream name is using the KV prefix and set MirrorDirect=true
	// - Else if Sources are set, normalize each source to KV naming and add subject transforms
	//   when External is nil or source bucket != current bucket
	var mirrorOut: StreamSource? = null
	var mirrorDirectOut: Boolean? = null
	var sourcesOut: List<StreamSource>? = null

	if (mirror != null) {
		var m = mirror
		if (!m.name.startsWith(KV_BUCKET_STREAM_NAME_PREFIX)) {
			m = m.copy(name = KV_BUCKET_STREAM_NAME_PREFIX + m.name)
		}
		mirrorOut = m
		mirrorDirectOut = true
	} else if (!sources.isNullOrEmpty()) {
		val normalized =
			sources.map { ss0 ->
				var ss = ss0

				val sourceBucketName =
					if (ss.name.startsWith(KV_BUCKET_STREAM_NAME_PREFIX)) {
						ss.name.removePrefix(KV_BUCKET_STREAM_NAME_PREFIX)
					} else {
						val original = ss.name
						ss = ss.copy(name = KV_BUCKET_STREAM_NAME_PREFIX + ss.name)
						original
					}

				// If External == null OR cross-bucket, inject subject transform
				if (ss.external == null || sourceBucketName != bucket) {
					ss.copy(
						subjectTransforms =
							listOf(
								SubjectTransform(
									source = kvSubjectsOf(sourceBucketName),
									destination = kvSubjectsOf(bucket),
								),
							),
					)
				}

				ss
			}
		sourcesOut = normalized
		// For sources mode (mirror not set), we explicitly set the subject used by this bucket
		subjects.clear()
		subjects.add(kvSubjectsOf(bucket))
	}

	return StreamConfig(
		name = KV_BUCKET_STREAM_NAME_PREFIX + bucket,
		description = description,
		maxConsumers = -1,
		maxMessages = -1,
		maxMessagesPerSubject = history,
		maxBytes = maxBytes,
		maxAge = ttl,
		maxMessageSize = maxMsgSize,
		storage = storage,
		discard = DiscardPolicy.New,
		replicas = replicas,
		duplicateWindow = duplicateWindow,
		placement = placement,
		mirror = mirrorOut,
		sources = sourcesOut,
		subjects = subjects,
		allowRollup = true,
		denyDelete = true,
		allowDirect = true,
		allowMessageTtl = allowMsgTtl,
		mirrorDirect = mirrorDirectOut,
		republish = republish,
		compression = compression,
		subjectDeleteMarkerTtl = subjectDeleteMarkerTtl,
		metadata = metadata,
	)
}

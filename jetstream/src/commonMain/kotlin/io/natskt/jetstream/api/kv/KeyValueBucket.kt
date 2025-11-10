package io.natskt.jetstream.api.kv

import io.natskt.api.Message
import io.natskt.api.Subject
import io.natskt.api.Subscription
import io.natskt.api.fullyQualified
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.KeyValueStatus
import io.natskt.jetstream.api.MessageGetRequest
import io.natskt.jetstream.api.PublishOptions
import io.natskt.jetstream.client.SEQUENCE_HEADER
import io.natskt.jetstream.client.TIME_STAMP_HEADER
import io.natskt.jetstream.internal.KV_BUCKET_STREAM_NAME_PREFIX
import io.natskt.jetstream.internal.PersistentRequestSubscription
import io.natskt.jetstream.internal.asKeyValueConfig
import io.natskt.jetstream.internal.asKeyValueStatus
import io.natskt.jetstream.internal.getMessage
import io.natskt.jetstream.internal.getStreamInfo
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val KV_SUBJECT_PREFIX = "\$KV."

public class KeyValueBucket internal constructor(
	js: JetStreamClient,
	inboxSubscription: Subscription,
	private val name: String,
	initialStatus: KeyValueStatus?,
	initialConfig: KeyValueConfig?,
) : PersistentRequestSubscription(js, inboxSubscription) {
	private var _status: KeyValueStatus? = initialStatus
	public val status: KeyValueStatus? get() = _status
	private var _config: KeyValueConfig? = initialConfig
	public val config: KeyValueConfig? get() = _config

	public suspend fun updateBucketStatus(): Result<KeyValueStatus> {
		val status =
			js.getStreamInfo(KV_BUCKET_STREAM_NAME_PREFIX + name).getOrElse {
				return Result.failure(it)
			}
		_status = status.asKeyValueStatus()
		_config = status.asKeyValueConfig()
		return Result.success(_status!!)
	}

	public suspend fun put(
		key: String,
		value: ByteArray,
	): ULong {
		val key = Subject.fullyQualified(key)

		val subject =
			buildString {
				append(KV_SUBJECT_PREFIX)
				append(name)
				append(".")
				append(key.raw)
			}

		val ack = js.publish(subject, value, null, null, PublishOptions())
		return ack.seq
	}

	public suspend fun get(
		key: String,
		revision: ULong? = null,
	): KeyValueEntry {
		val key = Subject.fullyQualified(key)

		val lastFor =
			buildString {
				append(KV_SUBJECT_PREFIX)
				append(name)
				append(".")
				append(key.raw)
			}

		val req =
			if (revision != null) {
				MessageGetRequest(seq = revision)
			} else {
				MessageGetRequest(lastFor = lastFor)
			}

		val message = js.getMessage(KV_BUCKET_STREAM_NAME_PREFIX + name, req)

		return message
			.map {
				KeyValueEntry.fromMessage(it)
			}.getOrThrow()
	}

	@OptIn(ExperimentalTime::class)
	internal fun KeyValueEntry.Companion.fromMessage(msg: Message): KeyValueEntry {
		val time =
			msg.headers
				?.get(TIME_STAMP_HEADER)
				?.firstOrNull()
				?.let { Instant.parseOrNull(it) } ?: Instant.DISTANT_PAST
		return KeyValueEntry(
			bucket = name,
			key = msg.subject.raw.removePrefix("$KV_SUBJECT_PREFIX$name."),
			value = msg.data ?: byteArrayOf(),
			revision =
				msg.headers
					?.get(SEQUENCE_HEADER)
					?.firstOrNull()
					?.toULong() ?: 0u,
			created =
				msg.headers
					?.get(TIME_STAMP_HEADER)
					?.firstOrNull()
					?.let { Instant.parseOrNull(it) } ?: Instant.DISTANT_PAST,
			delta = (Clock.System.now() - time).inWholeNanoseconds.toULong(),
			operation = null,
		)
	}

// 	public suspend fun watch(key: String): Flow<KeyValueEntry> {
//
// 	}
}

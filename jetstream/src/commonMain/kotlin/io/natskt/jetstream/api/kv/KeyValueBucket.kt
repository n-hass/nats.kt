package io.natskt.jetstream.api.kv

import io.natskt.api.Message
import io.natskt.api.Subject
import io.natskt.api.Subscription
import io.natskt.api.from
import io.natskt.api.fullyQualified
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.NUID
import io.natskt.jetstream.api.AckPolicy
import io.natskt.jetstream.api.ConsumerConfig
import io.natskt.jetstream.api.DeliverPolicy
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.KeyValueStatus
import io.natskt.jetstream.api.MessageGetRequest
import io.natskt.jetstream.api.PublishOptions
import io.natskt.jetstream.api.ReplayPolicy
import io.natskt.jetstream.client.KV_OPERATION_HEADER
import io.natskt.jetstream.client.SEQUENCE_HEADER
import io.natskt.jetstream.client.TIME_STAMP_HEADER
import io.natskt.jetstream.internal.KV_BUCKET_STREAM_NAME_PREFIX
import io.natskt.jetstream.internal.PersistentRequestSubscription
import io.natskt.jetstream.internal.PushConsumerImpl
import io.natskt.jetstream.internal.asKeyValueConfig
import io.natskt.jetstream.internal.asKeyValueStatus
import io.natskt.jetstream.internal.createFilteredConsumer
import io.natskt.jetstream.internal.deleteConsumer
import io.natskt.jetstream.internal.getMessage
import io.natskt.jetstream.internal.getStreamInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val KV_SUBJECT_PREFIX = "\$KV."
private const val KV_OPERATION_DELETE = "DEL"
private const val KV_OPERATION_PURGE = "PURGE"
private const val WATCH_CONSUMER_INACTIVE_THRESHOLD_NANOS = 5L * 60L * 1_000_000_000L
private const val ACK_V1_TOKEN_COUNT = 9
private const val ACK_V2_MIN_TOKEN_COUNT = 11
private const val ACK_DOMAIN_TOKEN_POS = 2
private const val ACK_STREAM_SEQ_TOKEN_POS = 7
private const val ACK_TIMESTAMP_TOKEN_POS = 9
private const val ACK_PENDING_TOKEN_POS = 10
private val WATCH_IDLE_HEARTBEAT = 5.seconds

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
				it.toKeyValueEntry(name)
			}.getOrThrow()
	}

	@OptIn(ExperimentalTime::class, InternalNatsApi::class)
	public suspend fun watch(key: String): Flow<KeyValueEntry> {
		require(key.isNotEmpty()) { "key cannot be blank" }
		val searchKey = Subject.from(key).raw

		val streamName = KV_BUCKET_STREAM_NAME_PREFIX + name
		val subjectPrefix = "$KV_SUBJECT_PREFIX$name."
		val filterSubject =
			buildString {
				append(subjectPrefix)
				append(searchKey)
			}

		val consumerName = NUID.nextSequence()

		val deliverySubscription = PushConsumerImpl.newSubscription(js.client, null)
		val consumer =
			PushConsumerImpl(
				name = consumerName,
				streamName = streamName,
				js = js,
				subscription = deliverySubscription,
				initialInfo = null,
			)

		val consumerConfig =
			ConsumerConfig(
				deliverPolicy = DeliverPolicy.LastPerSubject,
				ackPolicy = AckPolicy.None,
				maxDeliver = 1,
				filterSubject = filterSubject,
				replayPolicy = ReplayPolicy.Instant,
				flowControl = true,
				idleHeartbeat = WATCH_IDLE_HEARTBEAT,
				deliverSubject = deliverySubscription.subject.raw,
				numReplicas = 1,
				memoryStorage = true,
			)

		val consumerInfo =
			js
				.createFilteredConsumer(
					streamName = streamName,
					consumerName = consumerName,
					filterSubject = filterSubject,
					configuration = consumerConfig,
				).getOrThrow()

		consumer.info.value = consumerInfo

		return callbackFlow<KeyValueEntry> {
			val job =
				launch {
					while (isActive) {
						consumer.messages.collect {
							val entry = it.toKeyValueEntry(name)
							this@callbackFlow.send(entry)
						}
					}
				}

			awaitClose {
				job.cancel()
				consumer.close()
				js.client.scope.launch {
					deleteConsumer(streamName, consumer.name)
				}
			}
		}
	}
}

@OptIn(ExperimentalTime::class)
private fun Message.toKeyValueEntry(bucketName: String): KeyValueEntry {
	val subject = subject.raw
	val metadata = parseAckMetadata(replyTo)
	val operation = headers.extractOperation()
	val key = subject.removePrefix("$KV_SUBJECT_PREFIX$bucketName.")

	if (metadata == null) {
		val sequence =
			headers
				?.get(SEQUENCE_HEADER)
				?.firstOrNull()
				?.toULongOrNull() ?: 0u
		val created =
			headers
				?.get(TIME_STAMP_HEADER)
				?.firstOrNull()
				?.let {
					try {
						Instant.parse(it)
					} catch (_: Throwable) {
						null
					}
				} ?: Instant.DISTANT_PAST

		return KeyValueEntry(
			bucket = bucketName,
			key = key,
			value = data ?: ByteArray(0),
			revision = sequence,
			created = created,
			delta = 0u,
			operation = operation,
		)
	}

	return KeyValueEntry(
		bucket = bucketName,
		key = key,
		value = data ?: ByteArray(0),
		revision = metadata.streamSequence,
		created = ackTimestampToInstant(metadata.timestampNanos),
		delta = metadata.pending,
		operation = operation,
	)
}

private data class AckMetadata(
	val streamSequence: ULong,
	val pending: ULong,
	val timestampNanos: Long,
)

private fun parseAckMetadata(reply: Subject?): AckMetadata? {
	val raw = reply?.raw ?: return null
	val tokens = raw.split('.').toMutableList()
	if (tokens.size < ACK_V1_TOKEN_COUNT) return null
	if (tokens.size > ACK_V1_TOKEN_COUNT && tokens.size < ACK_V2_MIN_TOKEN_COUNT) return null
	if (tokens[0] != "\$JS" || tokens[1] != "ACK") return null

	if (tokens.size == ACK_V1_TOKEN_COUNT) {
		tokens.add(ACK_DOMAIN_TOKEN_POS, "")
		tokens.add(ACK_DOMAIN_TOKEN_POS + 1, "")
	} else if (tokens[ACK_DOMAIN_TOKEN_POS] == "_") {
		tokens[ACK_DOMAIN_TOKEN_POS] = ""
	}

	val streamSeq = tokens.getOrNull(ACK_STREAM_SEQ_TOKEN_POS)?.toULongOrNull() ?: return null
	val pending = tokens.getOrNull(ACK_PENDING_TOKEN_POS)?.toULongOrNull() ?: 0u
	val timestamp = tokens.getOrNull(ACK_TIMESTAMP_TOKEN_POS)?.toLongOrNull() ?: 0L
	return AckMetadata(streamSeq, pending, timestamp)
}

@OptIn(ExperimentalTime::class)
private fun ackTimestampToInstant(nanos: Long): Instant {
	if (nanos <= 0) return Instant.DISTANT_PAST
	val millis = nanos / 1_000_000
	val remainder = nanos % 1_000_000
	return Instant.fromEpochMilliseconds(millis).plus(remainder.nanoseconds)
}

private fun Map<String, List<String>>?.extractOperation(): KeyValueOperation? {
	val value = this?.get(KV_OPERATION_HEADER)?.firstOrNull() ?: return null
	return when (value) {
		KV_OPERATION_DELETE -> KeyValueOperation.Delete
		KV_OPERATION_PURGE -> KeyValueOperation.Purge
		else -> null
	}
}

package io.natskt.jetstream.api.kv

import io.natskt.api.Message
import io.natskt.api.Subject
import io.natskt.api.from
import io.natskt.api.fullyQualified
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.NUID
import io.natskt.internal.suspendLazy
import io.natskt.internal.throwOnInvalidToken
import io.natskt.jetstream.api.AckPolicy
import io.natskt.jetstream.api.ConsumerConfig
import io.natskt.jetstream.api.DeliverPolicy
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.KeyValueStatus
import io.natskt.jetstream.api.MessageGetRequest
import io.natskt.jetstream.api.PublishOptions
import io.natskt.jetstream.api.ReplayPolicy
import io.natskt.jetstream.client.KV_OPERATION_HEADER
import io.natskt.jetstream.client.ROLLUP_HEADER
import io.natskt.jetstream.client.ROLLUP_SUBJECT_VALUE
import io.natskt.jetstream.client.SEQUENCE_HEADER
import io.natskt.jetstream.client.TIME_STAMP_HEADER
import io.natskt.jetstream.internal.KV_BUCKET_STREAM_NAME_PREFIX
import io.natskt.jetstream.internal.PersistentRequestSubscription
import io.natskt.jetstream.internal.PushConsumerImpl
import io.natskt.jetstream.internal.asKeyValueConfig
import io.natskt.jetstream.internal.asKeyValueStatus
import io.natskt.jetstream.internal.createFilteredConsumer
import io.natskt.jetstream.internal.getMessage
import io.natskt.jetstream.internal.getStreamInfo
import io.natskt.jetstream.internal.parseAckMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val KV_SUBJECT_PREFIX = "\$KV."
private const val KV_OPERATION_DELETE = "DEL"
private const val KV_OPERATION_PURGE = "PURGE"
private val WATCH_IDLE_HEARTBEAT = 5.seconds
private val EMPTY_VALUE = ByteArray(0)

/**
 * A representation of a Key-Value bucket.
 * Use this to perform client operations on a bucket.
 *
 * This uses persistent requests, to be sure to call [close]
 * once you are finished, or wrap in [AutoCloseable.use]
 */
@OptIn(InternalNatsApi::class)
public class KeyValueBucket internal constructor(
	private val js: JetStreamClient,
	private val name: String,
	initialStatus: KeyValueStatus?,
	initialConfig: KeyValueConfig?,
) : AutoCloseable {
	init {
		name.throwOnInvalidToken()
	}

	private var _status: KeyValueStatus? = initialStatus
	public val status: KeyValueStatus? get() = _status
	private var _config: KeyValueConfig? = initialConfig
	public val config: KeyValueConfig? get() = _config

	private val req =
		suspendLazy {
			PersistentRequestSubscription(
				js,
				PersistentRequestSubscription.newSubscription(js.client),
			)
		}

	/**
	 * Refreshes the cached bucket status and configuration by querying JetStream for the latest stream info.
	 *
	 * @return a [Result] containing the latest [KeyValueStatus] or the error that occurred.
	 */
	public suspend fun updateBucketStatus(): Result<KeyValueStatus> {
		val status =
			req().getStreamInfo(KV_BUCKET_STREAM_NAME_PREFIX + name).getOrElse {
				return Result.failure(it)
			}
		_status = status.asKeyValueStatus()
		_config = status.asKeyValueConfig()
		return Result.success(_status!!)
	}

	/**
	 * Writes the provided [value] under [key], creating or replacing the latest revision without any precondition.
	 *
	 * @return the JetStream sequence associated with the stored revision.
	 */
	public suspend fun put(
		key: String,
		value: ByteArray,
	): ULong {
		val key = Subject.fullyQualified(key)
		val ack = js.publish(subjectForKey(key), value, null, null, PublishOptions())
		return ack.seq
	}

	/**
	 * Creates a new entry for [key] only if no existing entry is there (or it has been deleted/purged and not replaced since)
	 *
	 * This mirrors the server-side `create` semantics by allowing recreation after a delete/purge.
	 * @throws JetStreamApiException when the key is taken
	 */
	public suspend fun create(
		key: String,
		value: ByteArray,
	): ULong =
		try {
			update(key, value, 0u)
		} catch (error: JetStreamApiException) {
			val latest =
				runCatching { get(key) }
					.getOrNull()
			if (latest?.operation == KeyValueOperation.Delete || latest?.operation == KeyValueOperation.Purge) {
				update(key, value, latest.revision)
			} else {
				throw error
			}
		}

	/**
	 * Updates [key] with [value], asserting that the previous revision equals [lastRevision].
	 *
	 * @throws JetStreamApiException if the expected revision does not match or publishing fails.
	 */
	public suspend fun update(
		key: String,
		value: ByteArray,
		lastRevision: ULong,
	): ULong {
		val key = Subject.fullyQualified(key)
		val ack =
			js.publish(
				subjectForKey(key),
				value,
				null,
				null,
				PublishOptions(expectedLastSubjectSequence = lastRevision),
			)
		return ack.seq
	}

	/**
	 * Appends a delete marker for [key], optionally guarding on [lastRevision] for optimistic concurrency.
	 *
	 * @return the sequence of the delete revision.
	 */
	public suspend fun delete(
		key: String,
		lastRevision: ULong? = null,
	): ULong = deleteOrPurge(key, lastRevision, purge = false)

	/**
	 * Permanently purges the value and history for [key], optionally verifying [lastRevision] first.
	 *
	 * @return the sequence for the purge operation.
	 */
	public suspend fun purge(
		key: String,
		lastRevision: ULong? = null,
	): ULong = deleteOrPurge(key, lastRevision, purge = true)

	/**
	 * Retrieves the latest or specified [revision] for [key] and converts it to a [KeyValueEntry].
	 *
	 * @throws JetStreamApiException when JetStream rejects the lookup.
	 */
	public suspend fun get(
		key: String,
		revision: ULong? = null,
	): KeyValueEntry {
		val key = Subject.fullyQualified(key)

		val lastFor = subjectForKey(key)

		val req =
			if (revision != null) {
				MessageGetRequest(seq = revision)
			} else {
				MessageGetRequest(lastFor = lastFor)
			}

		val message = req().getMessage(KV_BUCKET_STREAM_NAME_PREFIX + name, req)

		return message.getOrThrow().toKeyValueEntry(name)
	}

	/**
	 * Watches revisions for [key], emitting a [Flow] of entries including future updates.
	 */
	@OptIn(ExperimentalTime::class, InternalNatsApi::class)
	public suspend fun watch(key: String): Flow<KeyValueEntry> =
		watchFiltered(key)
			.mapNotNull { it?.toKeyValueEntry(name) }

	/**
	 * Lists the keys currently stored in the bucket, optionally constrained by a key [filter].
	 */
	public suspend fun keys(filter: String? = null): List<String> =
		watchFiltered(filter ?: ">", headersOnly = true)
			.takeWhile { it != null }
			.map {
				it!!.subject.raw.removePrefix("$KV_SUBJECT_PREFIX$name.")
			}.toList()

	private suspend fun watchFiltered(
		filter: String,
		headersOnly: Boolean = false,
		policy: DeliverPolicy = DeliverPolicy.LastPerSubject,
		ordered: Boolean = false,
	): Flow<Message?> {
		require(filter.isNotEmpty()) { "subject cannot be blank" }
		val searchKey = Subject.from(filter).raw

		val streamName = KV_BUCKET_STREAM_NAME_PREFIX + name
		val subjectPrefix = "$KV_SUBJECT_PREFIX$name."
		val filterSubject =
			buildString {
				append(subjectPrefix)
				append(searchKey)
			}

		val consumerName = NUID.nextSequence()

		val deliverySubscription = PushConsumerImpl.newSubscription(js.client, null, eager = false)
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
				deliverPolicy = policy,
				ackPolicy = AckPolicy.None,
				maxDeliver = 1,
				filterSubject = filterSubject,
				replayPolicy = ReplayPolicy.Instant,
				flowControl = true,
				idleHeartbeat = WATCH_IDLE_HEARTBEAT,
				deliverSubject = deliverySubscription.subject.raw,
				numReplicas = 1,
				memoryStorage = true,
				headersOnly = headersOnly,
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

		val initPending: ULong = (consumerInfo.numPending).toULong()
		var received: ULong = 0u
		var snapshotDone = (initPending == 0uL)

		return consumer.messages
			.transform { msg ->
				emit(msg)

				if (!snapshotDone) {
					val meta = parseAckMetadata(msg.replyTo)
					val pending = meta?.pending ?: 0u
					received++

					if (received >= initPending || pending == 0uL) {
						snapshotDone = true
						emit(null)
					}
				}
			}.onCompletion {
				consumer.close()
			}
	}

	private fun subjectForKey(key: Subject): String =
		buildString {
			append(KV_SUBJECT_PREFIX)
			append(name)
			append(".")
			append(key.raw)
		}

	private suspend fun deleteOrPurge(
		key: String,
		lastRevision: ULong?,
		purge: Boolean,
	): ULong {
		val key = Subject.fullyQualified(key)
		val headers =
			buildMap {
				put(
					KV_OPERATION_HEADER,
					listOf(if (purge) KV_OPERATION_PURGE else KV_OPERATION_DELETE),
				)
				if (purge) {
					put(ROLLUP_HEADER, listOf(ROLLUP_SUBJECT_VALUE))
				}
			}

		val ack =
			js.publish(
				subjectForKey(key),
				EMPTY_VALUE,
				headers,
				null,
				PublishOptions(expectedLastSubjectSequence = lastRevision),
			)
		return ack.seq
	}

	/**
	 * Releases the persistent request subscription associated with this bucket instance.
	 */
	override fun close() {
		req.valueOrNull()?.close()
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
		created = metadata.timestamp,
		delta = metadata.pending,
		operation = operation,
	)
}

private fun Map<String, List<String>>?.extractOperation(): KeyValueOperation? {
	val value = this?.get(KV_OPERATION_HEADER)?.firstOrNull() ?: return null
	return when (value) {
		KV_OPERATION_DELETE -> KeyValueOperation.Delete
		KV_OPERATION_PURGE -> KeyValueOperation.Purge
		else -> null
	}
}

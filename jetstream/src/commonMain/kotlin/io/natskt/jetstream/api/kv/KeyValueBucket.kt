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
import io.natskt.jetstream.api.PurgeOptions
import io.natskt.jetstream.api.ReplayPolicy
import io.natskt.jetstream.api.internal.toGoDurationString
import io.natskt.jetstream.client.KV_OPERATION_HEADER
import io.natskt.jetstream.client.MARKER_REASON_HEADER
import io.natskt.jetstream.client.MSG_TTL_HEADER
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
import io.natskt.jetstream.internal.purgeStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
	 * Creates a new entry for [key] only if no existing entry is there (or it has been deleted/purged and not replaced since).
	 *
	 * This mirrors the server-side `create` semantics by allowing recreation after a delete/purge.
	 *
	 * When [ttl] is set, the entry carries a `Nats-TTL` header so the server expires it after the
	 * given duration. The bucket must have been created with [KeyValueConfig.limitMarkerTtl]
	 * (i.e. `allow_msg_ttl=true`) for the TTL to take effect; otherwise the server silently ignores it.
	 *
	 * @throws JetStreamApiException when the key is taken
	 */
	public suspend fun create(
		key: String,
		value: ByteArray,
		ttl: Duration? = null,
	): ULong {
		val existing =
			runCatching { get(key) }
				.getOrElse { error ->
					if (error is JetStreamApiException && error.error?.code == 404) {
						null
					} else {
						throw error
					}
				}

		val expectedRevision =
			when (existing?.operation) {
				KeyValueOperation.Delete, KeyValueOperation.Purge -> existing.revision
				else -> 0u
			}

		return update(key, value, expectedRevision, ttl)
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
		ttl: Duration? = null,
	): ULong {
		val key = Subject.fullyQualified(key)
		val ack =
			js.publish(
				subjectForKey(key),
				value,
				null,
				null,
				PublishOptions(expectedLastSubjectSequence = lastRevision, ttl = ttl),
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
	 * When [ttl] is set, the purge tombstone carries a `Nats-TTL` header so the marker itself ages
	 * out after the given duration (requires `allow_msg_ttl=true` on the bucket).
	 *
	 * @return the sequence for the purge operation.
	 */
	public suspend fun purge(
		key: String,
		lastRevision: ULong? = null,
		ttl: Duration? = null,
	): ULong = deleteOrPurge(key, lastRevision, purge = true, ttl = ttl)

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
	 *
	 * The flow is unbounded — it emits the initial snapshot followed by every subsequent update,
	 * and stays open until the collector cancels.
	 */
	public suspend fun watch(
		key: String,
		config: KeyValueWatchConfig = KeyValueWatchConfig.Default,
	): Flow<KeyValueEntry> = watch(listOf(key), config)

	/**
	 * Watches revisions for any key in [keys] on a single underlying consumer.
	 *
	 * Multi-filter watches require `nats-server` 2.10+; on older servers the create-consumer call
	 * fails with a typed [JetStreamApiException].
	 */
	public suspend fun watch(
		keys: List<String>,
		config: KeyValueWatchConfig = KeyValueWatchConfig.Default,
	): Flow<KeyValueEntry> {
		val (deliverPolicy, optStart) = config.deliverPolicyAndStart()
		val headersOnly = KeyValueWatchOption.MetaOnly in config.options
		val ignoreDelete = KeyValueWatchOption.IgnoreDelete in config.options

		val flow =
			watchFiltered(
				filters = keys.ifEmpty { listOf(">") },
				headersOnly = headersOnly,
				deliverPolicy = deliverPolicy,
				optStartSequence = optStart,
			).mapNotNull { it?.toKeyValueEntry(name) }
		return if (ignoreDelete) {
			flow.filter { it.operation != KeyValueOperation.Delete && it.operation != KeyValueOperation.Purge }
		} else {
			flow
		}
	}

	/**
	 * Watches every key in the bucket. Equivalent to `watch(">", config)`.
	 */
	public suspend fun watchAll(config: KeyValueWatchConfig = KeyValueWatchConfig.Default): Flow<KeyValueEntry> = watch(">", config)

	/**
	 * Returns every retained revision for [key], oldest-first, up to the bucket's history limit.
	 *
	 * Internally runs a one-shot `DeliverPolicy.All` consumer scoped to the key's subject and
	 * tears it down once the snapshot completes.
	 */
	public suspend fun history(key: String): List<KeyValueEntry> {
		val sub = Subject.from(key).raw
		return watchFiltered(
			filters = listOf(sub),
			headersOnly = false,
			deliverPolicy = DeliverPolicy.All,
		).takeWhile { it != null }
			.map { it!!.toKeyValueEntry(name) }
			.toList()
	}

	/**
	 * Lists the keys currently stored in the bucket, optionally constrained by a key [filter].
	 *
	 * Materializes the full list; for very large buckets prefer [consumeKeys] to stream keys
	 * without buffering.
	 */
	public suspend fun keys(filter: String? = null): List<String> = consumeKeys(filter).toList()

	/**
	 * Lists the keys whose subject matches any of [filters]. Requires `nats-server` 2.10+.
	 */
	public suspend fun keys(filters: List<String>): List<String> = consumeKeys(filters).toList()

	/**
	 * Streams the keys currently stored in the bucket, optionally constrained by [filter].
	 *
	 * The flow completes once the initial snapshot finishes, so callers can use it in a normal
	 * `collect { }` loop. Each emitted [String] is a key (not a fully-qualified subject).
	 */
	public suspend fun consumeKeys(filter: String? = null): Flow<String> = consumeKeys(listOf(filter ?: ">"))

	/**
	 * Streams the keys whose subject matches any of [filters]. Requires `nats-server` 2.10+.
	 *
	 * Tombstone entries (delete and purge markers) are filtered out, so the emitted strings
	 * represent live keys only — matching the behavior of `nats.go`'s `Keys()` / `ListKeys()`.
	 */
	public suspend fun consumeKeys(filters: List<String>): Flow<String> {
		val subjectPrefix = "$KV_SUBJECT_PREFIX$name."
		return watchFiltered(
			filters = filters.ifEmpty { listOf(">") },
			headersOnly = true,
			deliverPolicy = DeliverPolicy.LastPerSubject,
		).takeWhile { it != null }
			.filter { msg -> msg!!.headers.extractOperation() == null }
			.map { it!!.subject.raw.removePrefix(subjectPrefix) }
	}

	/**
	 * Removes delete and purge tombstones from the underlying stream.
	 *
	 * Markers older than [KeyValuePurgeOptions.deleteMarkersThreshold] (or all of them when
	 * [KeyValuePurgeOptions.noThreshold] is `true`) are removed entirely; markers within the
	 * threshold window have their preceding history truncated but the marker itself is kept so
	 * watchers can still observe the deletion.
	 */
	public suspend fun purgeDeletes(options: KeyValuePurgeOptions = KeyValuePurgeOptions.Default) {
		val markers =
			watchFiltered(
				filters = listOf(">"),
				headersOnly = false,
				deliverPolicy = DeliverPolicy.LastPerSubject,
			).takeWhile { it != null }
				.map { it!!.toKeyValueEntry(name) }
				.filter { it.operation == KeyValueOperation.Delete || it.operation == KeyValueOperation.Purge }
				.toList()

		val streamName = KV_BUCKET_STREAM_NAME_PREFIX + name
		val limit: Instant? =
			when {
				options.noThreshold -> null
				options.deleteMarkersThreshold <= Duration.ZERO -> null
				else -> Clock.System.now() - options.deleteMarkersThreshold
			}

		for (entry in markers) {
			val subject = subjectForKey(Subject.fullyQualified(entry.key))
			val keep: ULong? = if (limit != null && entry.created > limit) 1uL else null
			req().purgeStream(streamName, PurgeOptions(subject = subject, keep = keep)).getOrThrow()
		}
	}

	private fun KeyValueWatchConfig.deliverPolicyAndStart(): Pair<DeliverPolicy, Long?> {
		val include = KeyValueWatchOption.IncludeHistory in options
		val updates = KeyValueWatchOption.UpdatesOnly in options
		require(!(include && updates)) {
			"IncludeHistory and UpdatesOnly are mutually exclusive watch options"
		}

		fromRevision?.let { rev ->
			require(rev > 0u) { "fromRevision must be > 0 when set" }
			return DeliverPolicy.ByStartSequence to rev.toLong()
		}

		val policy =
			when {
				include -> DeliverPolicy.All
				updates -> DeliverPolicy.New
				else -> DeliverPolicy.LastPerSubject
			}
		return policy to null
	}

	private suspend fun watchFiltered(
		filters: List<String>,
		headersOnly: Boolean = false,
		deliverPolicy: DeliverPolicy = DeliverPolicy.LastPerSubject,
		optStartSequence: Long? = null,
	): Flow<Message?> {
		require(filters.isNotEmpty()) { "at least one filter subject is required" }
		val streamName = KV_BUCKET_STREAM_NAME_PREFIX + name
		val subjectPrefix = "$KV_SUBJECT_PREFIX$name."

		val filterSubjects =
			filters.map { raw ->
				require(raw.isNotEmpty()) { "filter subject must not be blank" }
				val validated = Subject.from(raw).raw
				subjectPrefix + validated
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

		val singleFilter = filterSubjects.singleOrNull()
		val consumerConfig =
			ConsumerConfig(
				deliverPolicy = deliverPolicy,
				optStartSequence = optStartSequence,
				ackPolicy = AckPolicy.None,
				maxDeliver = 1,
				filterSubject = singleFilter,
				filterSubjects = if (singleFilter == null) filterSubjects else null,
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
					filterSubject = singleFilter,
					configuration = consumerConfig,
				).getOrThrow()

		consumer.info.value = consumerInfo

		val updatesOnly = deliverPolicy == DeliverPolicy.New
		val initPending: ULong = consumerInfo.numPending.toULong()
		val emptySnapshot = !updatesOnly && initPending == 0uL

		return flow {
			if (emptySnapshot) {
				// Server reported zero pending on consumer creation — synthesize the snapshot-done
				// marker so bounded callers like keys()/history() complete without waiting for traffic.
				emit(null)
			}

			var received: ULong = 0u
			var snapshotDone = updatesOnly || emptySnapshot

			consumer.messages.collect { msg ->
				emit(msg)
				if (!snapshotDone) {
					val pending = parseAckMetadata(msg.replyTo)?.pending ?: 0u
					received++
					if (received >= initPending || pending == 0uL) {
						snapshotDone = true
						emit(null)
					}
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
		ttl: Duration? = null,
	): ULong {
		require(ttl == null || purge) { "Nats-TTL is only supported on purge tombstones, not delete markers" }
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
				if (ttl != null) {
					put(MSG_TTL_HEADER, listOf(ttl.toGoDurationString()))
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
	if (this == null) return null
	this[KV_OPERATION_HEADER]?.firstOrNull()?.let { kvOp ->
		return when (kvOp) {
			KV_OPERATION_DELETE -> KeyValueOperation.Delete
			KV_OPERATION_PURGE -> KeyValueOperation.Purge
			else -> null
		}
	}
	// Server-emitted markers (per-key TTL expiry, subject-delete-marker TTL) carry a
	// `Nats-Marker-Reason` header instead of the client-set `KV-Operation` header.
	this[MARKER_REASON_HEADER]?.firstOrNull()?.let { reason ->
		return when (reason) {
			"MaxAge", "Purge" -> KeyValueOperation.Purge
			"Remove" -> KeyValueOperation.Delete
			else -> null
		}
	}
	return null
}

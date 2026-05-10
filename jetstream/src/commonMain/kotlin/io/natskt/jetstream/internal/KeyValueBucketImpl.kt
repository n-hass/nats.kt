package io.natskt.jetstream.internal

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
import io.natskt.jetstream.api.KvKeyNotFoundException
import io.natskt.jetstream.api.MessageGetRequest
import io.natskt.jetstream.api.PublishOptions
import io.natskt.jetstream.api.PurgeOptions
import io.natskt.jetstream.api.ReplayPolicy
import io.natskt.jetstream.api.internal.toGoDurationString
import io.natskt.jetstream.api.kv.KeyValueBucket
import io.natskt.jetstream.api.kv.KeyValueConfig
import io.natskt.jetstream.api.kv.KeyValueEntry
import io.natskt.jetstream.api.kv.KeyValueOperation
import io.natskt.jetstream.api.kv.KeyValuePurgeOptions
import io.natskt.jetstream.api.kv.KeyValueWatchConfig
import io.natskt.jetstream.api.kv.KeyValueWatchOption
import io.natskt.jetstream.client.KV_OPERATION_HEADER
import io.natskt.jetstream.client.MARKER_REASON_HEADER
import io.natskt.jetstream.client.MSG_TTL_HEADER
import io.natskt.jetstream.client.ROLLUP_HEADER
import io.natskt.jetstream.client.ROLLUP_SUBJECT_VALUE
import io.natskt.jetstream.client.SEQUENCE_HEADER
import io.natskt.jetstream.client.SUBJECT_HEADER
import io.natskt.jetstream.client.TIME_STAMP_HEADER
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(InternalNatsApi::class)
internal class KeyValueBucketImpl(
	private val js: JetStreamClient,
	override val name: String,
	initialStatus: KeyValueStatus?,
	initialConfig: KeyValueConfig?,
) : KeyValueBucket {
	init {
		name.throwOnInvalidToken()
	}

	override val status = MutableStateFlow(initialStatus)
	override val config = MutableStateFlow(initialConfig)

	private val req =
		suspendLazy {
			PersistentRequestSubscription(
				js,
				PersistentRequestSubscription.newSubscription(js.client),
			)
		}

	override suspend fun updateBucketStatus(): Result<KeyValueStatus> {
		val streamInfo =
			req().getStreamInfo(KV_BUCKET_STREAM_NAME_PREFIX + name).getOrElse {
				return Result.failure(it)
			}
		val newStatus = streamInfo.asKeyValueStatus()
		status.value = newStatus
		config.value = streamInfo.asKeyValueConfig()
		return Result.success(newStatus)
	}

	override suspend fun put(
		key: String,
		value: ByteArray,
	): ULong {
		val key = Subject.fullyQualified(key)
		val ack = js.publish(subjectForKey(key), value, null, null, PublishOptions())
		return ack.seq
	}

	override suspend fun create(
		key: String,
		value: ByteArray,
		ttl: Duration?,
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

	override suspend fun update(
		key: String,
		value: ByteArray,
		lastRevision: ULong,
		ttl: Duration?,
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

	override suspend fun delete(
		key: String,
		lastRevision: ULong?,
	): ULong = deleteOrPurge(key, lastRevision, purge = false)

	override suspend fun purge(
		key: String,
		lastRevision: ULong?,
		ttl: Duration?,
	): ULong = deleteOrPurge(key, lastRevision, purge = true, ttl = ttl)

	override suspend fun get(
		key: String,
		revision: ULong?,
	): KeyValueEntry {
		val key = Subject.fullyQualified(key)

		val keySubject = subjectForKey(key)

		val req =
			if (revision != null) {
				MessageGetRequest(seq = revision)
			} else {
				MessageGetRequest(lastFor = keySubject)
			}

		val message = req().getMessage(KV_BUCKET_STREAM_NAME_PREFIX + name, req).getOrThrow()

		// `MessageGetRequest(seq = ...)` returns whatever sits at that sequence regardless of
		// subject — guard against the caller passing a sequence that belongs to a different key.
		// For direct-get the response message's `subject` is the inbox the reply was delivered
		// to, not the stored subject; the stored subject lives in the `Nats-Subject` header.
		if (revision != null) {
			val storedSubject = message.headers?.get(SUBJECT_HEADER)?.firstOrNull() ?: message.subject.raw
			if (storedSubject != keySubject) {
				throw KvKeyNotFoundException()
			}
		}

		return message.toKeyValueEntry(name)
	}

	override suspend fun watch(
		key: String,
		config: KeyValueWatchConfig,
	): Flow<KeyValueEntry> = watch(listOf(key), config)

	override suspend fun watch(
		keys: List<String>,
		config: KeyValueWatchConfig,
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

	override suspend fun watchAll(config: KeyValueWatchConfig): Flow<KeyValueEntry> = watch(">", config)

	override suspend fun history(key: String): List<KeyValueEntry> {
		val sub = Subject.from(key).raw
		return watchFiltered(
			filters = listOf(sub),
			headersOnly = false,
			deliverPolicy = DeliverPolicy.All,
		).takeWhile { it != null }
			.map { it!!.toKeyValueEntry(name) }
			.toList()
	}

	override suspend fun keys(filter: String?): List<String> = consumeKeys(filter).toList()

	override suspend fun keys(filters: List<String>): List<String> = consumeKeys(filters).toList()

	override suspend fun consumeKeys(filter: String?): Flow<String> = consumeKeys(listOf(filter ?: ">"))

	override suspend fun consumeKeys(filters: List<String>): Flow<String> {
		val subjectPrefix = "$KV_SUBJECT_PREFIX$name."
		return watchFiltered(
			filters = filters.ifEmpty { listOf(">") },
			headersOnly = true,
			deliverPolicy = DeliverPolicy.LastPerSubject,
		).takeWhile { it != null }
			.filter { msg -> msg!!.headers.extractOperation() == null }
			.map { it!!.subject.raw.removePrefix(subjectPrefix) }
	}

	override suspend fun purgeDeletes(options: KeyValuePurgeOptions) {
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

	override fun close() {
		req.valueOrNull()?.close()
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

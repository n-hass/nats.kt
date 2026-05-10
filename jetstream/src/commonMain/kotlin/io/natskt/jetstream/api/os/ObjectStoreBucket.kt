package io.natskt.jetstream.api.os

import io.natskt.api.Message
import io.natskt.api.SubjectToken
import io.natskt.api.from
import io.natskt.api.internal.InternalNatsApi
import io.natskt.internal.NUID
import io.natskt.internal.suspendLazy
import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.AckPolicy
import io.natskt.jetstream.api.ConsumerConfig
import io.natskt.jetstream.api.DeliverPolicy
import io.natskt.jetstream.api.JetStreamApiException
import io.natskt.jetstream.api.JetStreamClient
import io.natskt.jetstream.api.MessageGetRequest
import io.natskt.jetstream.api.PublishOptions
import io.natskt.jetstream.api.PurgeOptions
import io.natskt.jetstream.api.ReplayPolicy
import io.natskt.jetstream.client.ROLLUP_HEADER
import io.natskt.jetstream.client.ROLLUP_SUBJECT_VALUE
import io.natskt.jetstream.internal.OBJ_DEFAULT_CHUNK_SIZE
import io.natskt.jetstream.internal.PersistentRequestSubscription
import io.natskt.jetstream.internal.PushConsumerImpl
import io.natskt.jetstream.internal.Sha256Digester
import io.natskt.jetstream.internal.asObjectStoreConfig
import io.natskt.jetstream.internal.asObjectStoreStatus
import io.natskt.jetstream.internal.chunkSubjectFor
import io.natskt.jetstream.internal.createFilteredConsumer
import io.natskt.jetstream.internal.getMessageDirect
import io.natskt.jetstream.internal.getStreamInfo
import io.natskt.jetstream.internal.metaSubjectFor
import io.natskt.jetstream.internal.parseAckMetadata
import io.natskt.jetstream.internal.purgeStream
import io.natskt.jetstream.internal.toObjectStoreAllMetaSubject
import io.natskt.jetstream.internal.toObjectStoreStreamName
import io.natskt.jetstream.internal.updateStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.encodeToString
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val WATCH_IDLE_HEARTBEAT = 5.seconds
private const val NOT_FOUND_ERR_CODE = 10037

/**
 * A representation of an Object Store bucket: a JetStream-backed container of
 * arbitrarily-sized binary objects. Mirrors the Java `ObjectStore` interface
 * but exposes Kotlin idioms (`Flow`-based `watch`/`getStream`, suspend
 * operations, sealed [ObjectStoreException] for client-side errors).
 *
 * The bucket holds a persistent inbox subscription for direct-get and stream
 * info requests; call [close] (or wrap in [AutoCloseable.use]) when done.
 */
@OptIn(InternalNatsApi::class, ExperimentalTime::class)
public class ObjectStoreBucket internal constructor(
	private val js: JetStreamClient,
	public val name: String,
	initialStatus: ObjectStoreStatus?,
	initialConfig: ObjectStoreConfig?,
) : AutoCloseable {
	init {
		SubjectToken.from(name)
	}

	private var _status: ObjectStoreStatus? = initialStatus
	public val status: ObjectStoreStatus? get() = _status
	private var _config: ObjectStoreConfig? = initialConfig
	public val config: ObjectStoreConfig? get() = _config

	internal val streamName: String = toObjectStoreStreamName(name)

	private val req =
		suspendLazy {
			PersistentRequestSubscription(
				js,
				PersistentRequestSubscription.newSubscription(js.client),
			)
		}

	/**
	 * Refreshes the cached [status] and [config] by querying JetStream for
	 * the latest backing stream info.
	 */
	public suspend fun updateBucketStatus(): Result<ObjectStoreStatus> {
		val info = req().getStreamInfo(streamName).getOrElse { return Result.failure(it) }
		_status = info.asObjectStoreStatus()
		_config = info.asObjectStoreConfig()
		return Result.success(_status!!)
	}

	// ---------------------------------------------------------------------
	// PUT
	// ---------------------------------------------------------------------

	public suspend fun put(
		name: String,
		value: ByteArray,
	): ObjectInfo = put(ObjectMeta.objectName(name), value)

	public suspend fun put(
		meta: ObjectMeta,
		value: ByteArray,
	): ObjectInfo {
		validatePutMeta(meta)
		val chunkSize = resolveChunkSize(meta)
		val nuid = NUID.next()
		val chunkSubject = chunkSubjectFor(name, nuid)

		val oldInfo = getInfo(meta.name, includingDeleted = true)

		val digester = Sha256Digester()
		var totalSize = 0L
		var chunks = 0L

		try {
			var offset = 0
			while (offset < value.size) {
				val end = minOf(offset + chunkSize, value.size)
				val chunk = if (end - offset == value.size && offset == 0) value else value.copyOfRange(offset, end)
				digester.update(chunk)
				js.publish(chunkSubject, chunk)
				totalSize += chunk.size
				chunks++
				offset = end
			}

			val info =
				buildObjectInfo(
					meta = meta,
					nuid = nuid,
					size = totalSize,
					chunks = chunks,
					digest = digester.finishDigestEntry(),
					chunkSize = chunkSize,
				)
			val published = publishMeta(info)
			cleanupOldChunks(oldInfo)
			return published
		} catch (e: Throwable) {
			runCatching { js.purgeStream(streamName, PurgeOptions(subject = chunkSubject)) }
			throw e
		}
	}

	public suspend fun put(
		meta: ObjectMeta,
		source: Flow<ByteArray>,
	): ObjectInfo {
		validatePutMeta(meta)
		val chunkSize = resolveChunkSize(meta)
		val nuid = NUID.next()
		val chunkSubject = chunkSubjectFor(name, nuid)

		val oldInfo = getInfo(meta.name, includingDeleted = true)

		val digester = Sha256Digester()
		var totalSize = 0L
		var chunks = 0L
		var buffer = ByteArray(0)

		suspend fun emitChunk(chunk: ByteArray) {
			if (chunk.isEmpty()) return
			digester.update(chunk)
			js.publish(chunkSubject, chunk)
			totalSize += chunk.size
			chunks++
		}

		try {
			source.collect { incoming ->
				if (incoming.isEmpty()) return@collect
				var fed = 0
				if (buffer.isEmpty() && incoming.size >= chunkSize) {
					while (incoming.size - fed >= chunkSize) {
						emitChunk(incoming.copyOfRange(fed, fed + chunkSize))
						fed += chunkSize
					}
					if (fed < incoming.size) {
						buffer = incoming.copyOfRange(fed, incoming.size)
					}
					return@collect
				}
				val combined = ByteArray(buffer.size + (incoming.size - fed))
				buffer.copyInto(combined)
				incoming.copyInto(combined, buffer.size, fed)
				buffer = combined
				while (buffer.size >= chunkSize) {
					emitChunk(buffer.copyOfRange(0, chunkSize))
					buffer = buffer.copyOfRange(chunkSize, buffer.size)
				}
			}
			if (buffer.isNotEmpty()) {
				emitChunk(buffer)
				buffer = ByteArray(0)
			}

			val info =
				buildObjectInfo(
					meta = meta,
					nuid = nuid,
					size = totalSize,
					chunks = chunks,
					digest = digester.finishDigestEntry(),
					chunkSize = chunkSize,
				)
			val published = publishMeta(info)
			cleanupOldChunks(oldInfo)
			return published
		} catch (e: Throwable) {
			runCatching { js.purgeStream(streamName, PurgeOptions(subject = chunkSubject)) }
			throw e
		}
	}

	// ---------------------------------------------------------------------
	// GET
	// ---------------------------------------------------------------------

	public suspend fun get(name: String): GetResult {
		val info = resolveForRead(name)

		val data: ByteArray
		val digester = Sha256Digester()
		when {
			info.chunks == 0L -> {
				data = ByteArray(0)
				// Empty data still feeds the digester so the empty digest matches.
			}
			info.chunks == 1L -> {
				val nuid = info.nuid ?: throw IllegalStateException("info missing nuid")
				val msg =
					req()
						.getMessageDirect(
							info.bucketStreamName(),
							MessageGetRequest(lastFor = chunkSubjectFor(info.bucket, nuid)),
						).getOrThrow()
				data = msg.data ?: ByteArray(0)
				digester.update(data)
			}
			else -> {
				data = consumeAllChunks(info, digester)
			}
		}

		validatePayload(info, data.size.toLong(), digester.finishDigestEntry())
		return GetResult(info, data)
	}

	public suspend fun getStream(name: String): ObjectStreamResult {
		val info = resolveForRead(name)
		val flow: Flow<ByteArray> =
			when {
				info.chunks == 0L -> flow { /* empty */ }
				info.chunks == 1L ->
					flow {
						val nuid = info.nuid ?: throw IllegalStateException("info missing nuid")
						val msg =
							req()
								.getMessageDirect(
									info.bucketStreamName(),
									MessageGetRequest(lastFor = chunkSubjectFor(info.bucket, nuid)),
								).getOrThrow()
						val data = msg.data ?: ByteArray(0)
						val digester = Sha256Digester()
						digester.update(data)
						validatePayload(info, data.size.toLong(), digester.finishDigestEntry())
						if (data.isNotEmpty()) emit(data)
					}
				else -> chunksFlow(info)
			}
		return ObjectStreamResult(info, flow)
	}

	public suspend fun getInfo(
		name: String,
		includingDeleted: Boolean = false,
	): ObjectInfo? {
		val subject = metaSubjectFor(this.name, name)
		val msg =
			req()
				.getMessageDirect(streamName, MessageGetRequest(lastFor = subject))
				.getOrElse { error ->
					if (error.isNotFound()) return null
					throw error
				}
		val data = msg.data ?: throw IllegalStateException("empty meta message")
		val info =
			wireJsonFormat
				.decodeFromString<ObjectInfo>(data.decodeToString())
				.copy(modified = parseStoredMessageTime(msg.time))
		return if (includingDeleted || !info.deleted) info else null
	}

	public suspend fun getList(): List<ObjectInfo> =
		watchMetaInternal(
			filterSubject = toObjectStoreAllMetaSubject(name),
			deliverPolicy = DeliverPolicy.LastPerSubject,
			waitForSnapshotDone = true,
		).takeWhile { it != null }
			.filterNotNull()
			.toList()
			.filter { !it.deleted }

	// ---------------------------------------------------------------------
	// DELETE
	// ---------------------------------------------------------------------

	public suspend fun delete(name: String): ObjectInfo {
		val info = getInfo(name, includingDeleted = true) ?: throw ObjectNotFound(name)
		if (info.deleted) return info

		val tombstone =
			info.copy(
				deleted = true,
				size = 0,
				chunks = 0,
				digest = null,
				modified = null,
			)
		val deleted = publishMeta(tombstone)

		if (info.nuid != null) {
			runCatching {
				js.purgeStream(streamName, PurgeOptions(subject = chunkSubjectFor(this.name, info.nuid)))
			}
		}
		return deleted
	}

	// ---------------------------------------------------------------------
	// WATCH
	// ---------------------------------------------------------------------

	public suspend fun watch(options: Set<ObjectStoreWatchOption> = emptySet()): Flow<ObjectInfo?> {
		val deliverPolicy =
			when {
				ObjectStoreWatchOption.UpdatesOnly in options -> DeliverPolicy.New
				ObjectStoreWatchOption.IncludeHistory in options -> DeliverPolicy.All
				else -> DeliverPolicy.LastPerSubject
			}
		val ignoreDeletes = ObjectStoreWatchOption.IgnoreDelete in options
		val waitForSnapshot = deliverPolicy != DeliverPolicy.New

		return watchMetaInternal(
			filterSubject = toObjectStoreAllMetaSubject(name),
			deliverPolicy = deliverPolicy,
			waitForSnapshotDone = waitForSnapshot,
		).transform { info ->
			when {
				info == null -> emit(null)
				ignoreDeletes && info.deleted -> Unit
				else -> emit(info)
			}
		}
	}

	// ---------------------------------------------------------------------
	// META UPDATE / LINKS / SEAL
	// ---------------------------------------------------------------------

	public suspend fun updateMeta(
		name: String,
		newMeta: ObjectMeta,
	): ObjectInfo {
		val current = getInfo(name, includingDeleted = true) ?: throw ObjectNotFound(name)
		if (current.deleted) throw ObjectIsDeleted(name)

		val nameChanged = name != newMeta.name
		if (nameChanged) {
			val existing = getInfo(newMeta.name, includingDeleted = false)
			if (existing != null) throw ObjectAlreadyExists(newMeta.name)
		}

		val updated =
			current.copy(
				name = newMeta.name,
				description = newMeta.description,
				headers = newMeta.headers,
				metadata = newMeta.metadata,
			)
		val published = publishMeta(updated)

		if (nameChanged) {
			runCatching {
				js.purgeStream(streamName, PurgeOptions(subject = metaSubjectFor(this.name, name)))
			}
		}
		return published
	}

	public suspend fun addLink(
		objectName: String,
		target: ObjectInfo,
	): ObjectInfo {
		if (target.deleted) throw ObjectIsDeleted(target.name)
		if (target.isLink) throw CantLinkToLink()

		val existing = getInfo(objectName, includingDeleted = false)
		if (existing != null && !existing.isLink) throw ObjectAlreadyExists(objectName)

		val info =
			ObjectInfo(
				name = objectName,
				bucket = name,
				nuid = NUID.next(),
				options =
					ObjectMetaOptions(
						link = ObjectLink.objectLink(target.bucket, target.name),
					),
			)
		return publishMeta(info)
	}

	public suspend fun addBucketLink(
		objectName: String,
		target: ObjectStoreBucket,
	): ObjectInfo {
		val existing = getInfo(objectName, includingDeleted = false)
		if (existing != null && !existing.isLink) throw ObjectAlreadyExists(objectName)

		val info =
			ObjectInfo(
				name = objectName,
				bucket = name,
				nuid = NUID.next(),
				options =
					ObjectMetaOptions(
						link = ObjectLink.bucket(target.name),
					),
			)
		return publishMeta(info)
	}

	public suspend fun seal(): ObjectStoreStatus {
		val current = req().getStreamInfo(streamName).getOrThrow()
		val sealed = req().updateStream(current.config.copy(sealed = true)).getOrThrow()
		_status = sealed.asObjectStoreStatus()
		_config = sealed.asObjectStoreConfig()
		return sealed.asObjectStoreStatus()
	}

	override fun close() {
		req.valueOrNull()?.close()
	}

	// ---------------------------------------------------------------------
	// Internals
	// ---------------------------------------------------------------------

	private fun ObjectInfo.bucketStreamName(): String =
		if (bucket == this@ObjectStoreBucket.name) {
			streamName
		} else {
			toObjectStoreStreamName(bucket)
		}

	private fun validatePutMeta(meta: ObjectMeta) {
		if (meta.options?.link != null) throw LinkNotAllowedOnPut()
	}

	private fun resolveChunkSize(meta: ObjectMeta): Int {
		val requested = meta.options?.maxChunkSize ?: 0
		return if (requested <= 0) OBJ_DEFAULT_CHUNK_SIZE else requested
	}

	private fun buildObjectInfo(
		meta: ObjectMeta,
		nuid: String,
		size: Long,
		chunks: Long,
		digest: String,
		chunkSize: Int,
	): ObjectInfo {
		val baseOptions = meta.options
		val needsExplicitChunkSize = (baseOptions?.maxChunkSize ?: 0) > 0
		val options =
			when {
				needsExplicitChunkSize -> baseOptions
				baseOptions?.link != null -> baseOptions
				else -> null
			}
		return ObjectInfo(
			name = meta.name,
			description = meta.description,
			headers = meta.headers,
			metadata = meta.metadata,
			options = options,
			bucket = name,
			nuid = nuid,
			size = size,
			chunks = chunks,
			digest = digest,
			deleted = false,
		)
	}

	private suspend fun publishMeta(info: ObjectInfo): ObjectInfo {
		val payload = wireJsonFormat.encodeToString(info).encodeToByteArray()
		val headers = mapOf(ROLLUP_HEADER to listOf(ROLLUP_SUBJECT_VALUE))
		js.publish(metaSubjectFor(name, info.name), payload, headers, null, PublishOptions())
		return info.copy(modified = Clock.System.now())
	}

	private suspend fun cleanupOldChunks(old: ObjectInfo?) {
		val nuid = old?.nuid ?: return
		runCatching {
			js.purgeStream(streamName, PurgeOptions(subject = chunkSubjectFor(name, nuid)))
		}
	}

	private suspend fun resolveForRead(objectName: String): ObjectInfo {
		val info = getInfo(objectName, includingDeleted = false) ?: throw ObjectNotFound(objectName)
		if (!info.isLink) return info

		val link = info.options!!.link!!
		if (link.isBucketLink) throw GetLinkToBucket()

		val targetName = link.name!!
		return if (link.bucket == name) {
			resolveForRead(targetName)
		} else {
			val other = ObjectStoreBucket(js, link.bucket, null, null)
			try {
				other.resolveForRead(targetName)
			} finally {
				other.close()
			}
		}
	}

	private fun validatePayload(
		info: ObjectInfo,
		actualSize: Long,
		computedDigest: String,
	) {
		if (actualSize != info.size) throw GetSizeMismatch(info.size, actualSize)
		if (info.digest != null && info.digest != computedDigest) throw GetDigestMismatch(info.digest, computedDigest)
	}

	private suspend fun consumeAllChunks(
		info: ObjectInfo,
		digester: Sha256Digester,
	): ByteArray {
		val nuid = info.nuid ?: throw IllegalStateException("info missing nuid")
		val expected = info.chunks
		val total = info.size.toInt()
		val data = ByteArray(total)
		var offset = 0
		var received = 0L

		val chunkSubject = chunkSubjectFor(info.bucket, nuid)
		val (consumer, _) = createEphemeralChunkConsumer(info.bucketStreamName(), chunkSubject)
		try {
			consumer.messages
				.take(expected.toInt())
				.collect { msg ->
					val chunk = msg.data ?: ByteArray(0)
					if (offset + chunk.size > total) throw GetSizeMismatch(info.size, (offset + chunk.size).toLong())
					chunk.copyInto(data, offset)
					digester.update(chunk)
					offset += chunk.size
					received++
				}
		} finally {
			consumer.close()
		}

		if (received != expected) throw GetChunksMismatch(expected, received)
		return data
	}

	private suspend fun chunksFlow(info: ObjectInfo): Flow<ByteArray> =
		flow {
			val nuid = info.nuid ?: throw IllegalStateException("info missing nuid")
			val expected = info.chunks
			var received = 0L
			var totalSize = 0L
			val digester = Sha256Digester()

			val chunkSubject = chunkSubjectFor(info.bucket, nuid)
			val (consumer, _) = createEphemeralChunkConsumer(info.bucketStreamName(), chunkSubject)
			try {
				consumer.messages
					.take(expected.toInt())
					.collect { msg ->
						val chunk = msg.data ?: ByteArray(0)
						digester.update(chunk)
						totalSize += chunk.size
						received++
						if (chunk.isNotEmpty()) emit(chunk)
					}
			} finally {
				consumer.close()
			}

			if (received != expected) throw GetChunksMismatch(expected, received)
			validatePayload(info, totalSize, digester.finishDigestEntry())
		}

	private suspend fun createEphemeralChunkConsumer(
		stream: String,
		filterSubject: String,
	): Pair<PushConsumerImpl, String> {
		val consumerName = NUID.nextSequence()
		val deliverySubscription = PushConsumerImpl.newSubscription(js.client, null, eager = false)
		val consumer =
			PushConsumerImpl(
				name = consumerName,
				streamName = stream,
				js = js,
				subscription = deliverySubscription,
				initialInfo = null,
			)
		val consumerConfig =
			ConsumerConfig(
				deliverPolicy = DeliverPolicy.All,
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
		val info =
			js
				.createFilteredConsumer(stream, consumerName, filterSubject, consumerConfig)
				.getOrThrow()
		consumer.info.value = info
		return consumer to consumerName
	}

	private fun watchMetaInternal(
		filterSubject: String,
		deliverPolicy: DeliverPolicy,
		waitForSnapshotDone: Boolean,
	): Flow<ObjectInfo?> =
		flow {
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
					deliverPolicy = deliverPolicy,
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
			try {
				val info =
					js
						.createFilteredConsumer(streamName, consumerName, filterSubject, consumerConfig)
						.getOrThrow()
				consumer.info.value = info

				val initialPending: ULong = info.numPending.toULong()
				var received: ULong = 0u
				var snapshotDone = !waitForSnapshotDone || initialPending == 0uL

				if (waitForSnapshotDone && initialPending == 0uL) {
					emit(null)
				}

				consumer.messages.collect { msg ->
					val decoded = decodeMetaMessage(msg)
					emit(decoded)
					if (!snapshotDone) {
						received++
						val pending = parseAckMetadata(msg.replyTo)?.pending ?: 0u
						if (received >= initialPending || pending == 0uL) {
							snapshotDone = true
							emit(null)
						}
					}
				}
			} finally {
				consumer.close()
			}
		}

	private fun decodeMetaMessage(msg: Message): ObjectInfo? {
		val data = msg.data ?: return null
		if (data.isEmpty()) return null
		return wireJsonFormat
			.decodeFromString<ObjectInfo>(data.decodeToString())
			.copy(modified = parseAckMetadata(msg.replyTo)?.timestamp)
	}
}

private fun Throwable.isNotFound(): Boolean {
	if (this !is JetStreamApiException) return false
	val err = error ?: return false
	if (err.code == 404) return true
	if (err.errCode == 404) return true
	if (err.errCode == NOT_FOUND_ERR_CODE) return true
	return false
}

private fun parseStoredMessageTime(time: String): Instant? =
	try {
		Instant.parse(time)
	} catch (_: Throwable) {
		null
	}

package io.natskt.jetstream.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class ApiError(
	val code: Int? = null,
	@SerialName("err_code")
	val errCode: Int? = null,
	val description: String? = null,
) : ApiResponse

@Serializable
public data class ApiStats(
	val total: Int = 0,
	val errors: Int = 0,
	val inflight: Int = 0,
)

@Serializable
public data class ApiPaged(
	val total: Int = 0,
	val offset: Int = 0,
	val limit: Int = 0,
)

@Serializable
public data class AccountLimits(
	@SerialName("max_memory")
	val maxMemory: Long? = null,
	@SerialName("max_storage")
	val maxStorage: Long? = null,
	@SerialName("max_streams")
	val maxStreams: Int? = null,
	@SerialName("max_consumers")
	val maxConsumers: Int? = null,
	@SerialName("max_ack_pending")
	val maxAckPending: Int? = null,
	@SerialName("memory_max_stream_bytes")
	val memoryMaxStreamBytes: Long? = null,
	@SerialName("storage_max_stream_bytes")
	val storageMaxStreamBytes: Long? = null,
	@SerialName("max_stream_bytes")
	val maxStreamBytes: Long? = null,
	@SerialName("max_bytes_required")
	val maxBytesRequired: Long? = null,
)

@Serializable
public data class AccountTier(
	val memory: Long = 0,
	val storage: Long = 0,
	val streams: Int = 0,
	val consumers: Int = 0,
	val limits: AccountLimits? = null,
	val api: ApiStats? = null,
)

@Serializable
public data class AccountInfoResponse(
	val type: String? = null,
	val error: ApiError? = null,
	val memory: Long = 0,
	val storage: Long = 0,
	@SerialName("reserved_memory")
	val reservedMemory: Long? = null,
	@SerialName("reserved_storage")
	val reservedStorage: Long? = null,
	val streams: Int = 0,
	val consumers: Int = 0,
	val domain: String? = null,
	@SerialName("api")
	val apiStats: ApiStats? = null,
	val limits: AccountLimits? = null,
	@SerialName("tiered_limits")
	val tieredLimits: Map<String, AccountTier>? = null,
) : JetStreamApiResponse

@Serializable
public data class PublishAck(
	val stream: String,
	val seq: Long,
	val duplicate: Boolean = false,
	val error: ApiError? = null,
)

@Serializable
public enum class RetentionPolicy {
	@SerialName("limits")
	Limits,

	@SerialName("interest")
	Interest,

	@SerialName("workqueue")
	WorkQueue,
}

@Serializable
public enum class DiscardPolicy {
	@SerialName("old")
	Old,

	@SerialName("new")
	New,
}

@Serializable
public enum class StorageType {
	@SerialName("file")
	File,

	@SerialName("memory")
	Memory,
}

@Serializable
public enum class StreamCompression {
	@SerialName("none")
	None,

	@SerialName("s2")
	S2,
}

@Serializable
public data class SubjectTransform(
	val source: String,
	val destination: String,
)

@Serializable
public data class ExternalStream(
	val api: String,
	val deliver: String? = null,
)

@Serializable
public data class StreamPlacement(
	val cluster: String? = null,
	val tags: List<String>? = null,
	val domain: String? = null,
)

@Serializable
public data class StreamRepublish(
	val source: String,
	val destination: String,
	@SerialName("source_keep_chars")
	val sourceKeepChars: Int? = null,
	val headers: Boolean? = null,
)

@Serializable
public data class StreamSource(
	val name: String,
	@SerialName("opt_start_seq")
	val optStartSequence: Long? = null,
	@SerialName("opt_start_time")
	val optStartTime: String? = null,
	@SerialName("filter_subject")
	val filterSubject: String? = null,
	@SerialName("filter_subjects")
	val filterSubjects: List<String>? = null,
	val external: ExternalStream? = null,
	val domain: String? = null,
	@SerialName("subject_transforms")
	val subjectTransforms: List<SubjectTransform>? = null,
)

@Serializable
public data class StreamSourceInfo(
	val name: String,
	@SerialName("opt_start_seq")
	val optStartSequence: Long? = null,
	@SerialName("opt_start_time")
	val optStartTime: String? = null,
	@SerialName("filter_subject")
	val filterSubject: String? = null,
	@SerialName("filter_subjects")
	val filterSubjects: List<String>? = null,
	val external: ExternalStream? = null,
	val domain: String? = null,
	@SerialName("subject_transforms")
	val subjectTransforms: List<SubjectTransform>? = null,
	val lag: Long? = null,
	val active: Long? = null,
	val error: ApiError? = null,
)

@Serializable
public data class StreamAlternate(
	val name: String,
	val domain: String? = null,
	val cluster: String? = null,
	val stream: String? = null,
	val external: ExternalStream? = null,
)

@Serializable
public data class StreamConfiguration(
	val name: String,
	val description: String? = null,
	val subjects: List<String>? = null,
	val retention: RetentionPolicy? = null,
	@SerialName("max_consumers")
	val maxConsumers: Int? = null,
	@SerialName("max_msgs")
	val maxMessages: Long? = null,
	@SerialName("max_msgs_per_subject")
	val maxMessagesPerSubject: Long? = null,
	@SerialName("max_bytes")
	val maxBytes: Long? = null,
	@SerialName("max_age")
	val maxAgeNanos: Long? = null,
	@SerialName("max_msg_size")
	val maxMessageSize: Int? = null,
	val storage: StorageType? = null,
	val discard: DiscardPolicy? = null,
	@SerialName("discard_new_per")
	val discardNewPerSubject: String? = null,
	@SerialName("num_replicas")
	val replicas: Int? = null,
	@SerialName("no_ack")
	val noAck: Boolean? = null,
	@SerialName("template_owner")
	val templateOwner: String? = null,
	@SerialName("duplicate_window")
	val duplicateWindow: Long? = null,
	val placement: StreamPlacement? = null,
	val mirror: StreamSource? = null,
	val sources: List<StreamSource>? = null,
	@SerialName("allow_rollup_hdrs")
	val allowRollupHeaders: Boolean? = null,
	@SerialName("deny_delete")
	val denyDelete: Boolean? = null,
	@SerialName("deny_purge")
	val denyPurge: Boolean? = null,
	@SerialName("allow_direct")
	val allowDirect: Boolean? = null,
	@SerialName("mirror_direct")
	val mirrorDirect: Boolean? = null,
	@SerialName("republish")
	val republish: StreamRepublish? = null,
	val sealed: Boolean? = null,
	@SerialName("compression")
	val compression: StreamCompression? = null,
	val metadata: Map<String, String>? = null,
)

@Serializable
public data class StreamLostData(
	val msgIds: List<String>? = null,
	val messages: Long? = null,
	val bytes: Long? = null,
)

@Serializable
public data class StreamState(
	val messages: Long = 0,
	val bytes: Long = 0,
	@SerialName("first_seq")
	val firstSequence: Long = 0,
	@SerialName("first_ts")
	val firstTimestamp: String? = null,
	@SerialName("last_seq")
	val lastSequence: Long = 0,
	@SerialName("last_ts")
	val lastTimestamp: String? = null,
	@SerialName("consumer_count")
	val consumerCount: Int? = null,
	@SerialName("num_subjects")
	val subjectCount: Int? = null,
	val subjects: Map<String, Long>? = null,
	val deleted: List<Long>? = null,
	@SerialName("num_deleted")
	val deletedCount: Long? = null,
	@SerialName("num_waiting")
	val waitingCount: Int? = null,
	@SerialName("num_ack_pending")
	val ackPendingCount: Int? = null,
	@SerialName("num_redelivered")
	val redeliveredCount: Int? = null,
	@SerialName("num_pending")
	val pendingCount: Long? = null,
	val lost: List<StreamLostData>? = null,
)

@Serializable
public data class ClusterInfo(
	val name: String? = null,
	val leader: String? = null,
	val replicas: List<PeerInfo>? = null,
)

@Serializable
public data class PeerInfo(
	val name: String,
	val current: Boolean? = null,
	val offline: Boolean? = null,
	val active: Long? = null,
	val lag: Long? = null,
	val peer: String? = null,
	val cluster: String? = null,
)

@Serializable
public data class StreamInfo(
	val type: String? = null,
	val config: StreamConfiguration,
	val created: String? = null,
	val state: StreamState,
	val cluster: ClusterInfo? = null,
	val mirror: StreamSourceInfo? = null,
	val sources: List<StreamSourceInfo>? = null,
	val alternates: List<StreamAlternate>? = null,
) : JetStreamApiResponse

@Serializable
public data class StreamListResponse(
	val type: String? = null,
	val error: ApiError? = null,
	val total: Int = 0,
	val offset: Int = 0,
	val limit: Int = 0,
	val streams: List<StreamInfo> = emptyList(),
)

@Serializable
public data class StreamNamesResponse(
	val type: String? = null,
	val error: ApiError? = null,
	val total: Int = 0,
	val offset: Int = 0,
	val limit: Int = 0,
	val streams: List<String> = emptyList(),
)

@Serializable
public enum class DeliverPolicy {
	@SerialName("all")
	All,

	@SerialName("last")
	Last,

	@SerialName("new")
	New,

	@SerialName("by_start_sequence")
	ByStartSequence,

	@SerialName("by_start_time")
	ByStartTime,

	@SerialName("last_per_subject")
	LastPerSubject,
}

@Serializable
public enum class AckPolicy {
	@SerialName("none")
	None,

	@SerialName("all")
	All,

	@SerialName("explicit")
	Explicit,
}

@Serializable
public enum class ReplayPolicy {
	@SerialName("instant")
	Instant,

	@SerialName("original")
	Original,
}

@Serializable
public data class ConsumerConfiguration(
	@SerialName("durable_name")
	val durableName: String? = null,
	val description: String? = null,
	@SerialName("deliver_subject")
	val deliverSubject: String? = null,
	@SerialName("deliver_group")
	val deliverGroup: String? = null,
	@SerialName("deliver_policy")
	val deliverPolicy: DeliverPolicy? = null,
	@SerialName("opt_start_seq")
	val optStartSequence: Long? = null,
	@SerialName("opt_start_time")
	val optStartTime: String? = null,
	@SerialName("ack_policy")
	val ackPolicy: AckPolicy? = null,
	@SerialName("ack_wait")
	val ackWait: Long? = null,
	@SerialName("max_deliver")
	val maxDeliver: Int? = null,
	val backoff: List<Long>? = null,
	@SerialName("filter_subject")
	val filterSubject: String? = null,
	@SerialName("filter_subjects")
	val filterSubjects: List<String>? = null,
	@SerialName("replay_policy")
	val replayPolicy: ReplayPolicy? = null,
	@SerialName("sample_freq")
	val sampleFrequency: String? = null,
	@SerialName("rate_limit_bps")
	val rateLimitBps: Long? = null,
	@SerialName("max_ack_pending")
	val maxAckPending: Int? = null,
	@SerialName("max_waiting")
	val maxWaiting: Int? = null,
	@SerialName("max_batch")
	val maxBatch: Int? = null,
	@SerialName("max_expires")
	val maxExpires: Long? = null,
	@SerialName("max_bytes")
	val maxBytes: Long? = null,
	@SerialName("inactive_threshold")
	val inactiveThreshold: Long? = null,
	@SerialName("num_replicas")
	val numReplicas: Int? = null,
	@SerialName("mem_storage")
	val memoryStorage: Boolean? = null,
	val metadata: Map<String, String>? = null,
	@SerialName("headers_only")
	val headersOnly: Boolean? = null,
	@SerialName("idle_heartbeat")
	val idleHeartbeat: Long? = null,
	@SerialName("flow_control")
	val flowControl: Boolean? = null,
	val direct: Boolean? = null,
	@SerialName("max_pull_waiting")
	val maxPullWaiting: Int? = null,
	@SerialName("max_request_batch")
	val maxRequestBatch: Int? = null,
	@SerialName("max_request_expires")
	val maxRequestExpires: Long? = null,
	@SerialName("max_request_max_bytes")
	val maxRequestMaxBytes: Long? = null,
	@SerialName("deliver_metrics")
	val deliverMetrics: Boolean? = null,
)

@Serializable
public data class SequencePair(
	@SerialName("consumer_seq")
	val consumerSequence: Long = 0,
	@SerialName("stream_seq")
	val streamSequence: Long = 0,
)

@Serializable
public data class SequenceInfo(
	@SerialName("consumer_seq")
	val consumerSequence: Long = 0,
	@SerialName("stream_seq")
	val streamSequence: Long = 0,
	@SerialName("last_active")
	val lastActive: String? = null,
)

@Serializable
public data class ConsumerInfo(
	val type: String? = null,
	val error: ApiError? = null,
	@SerialName("stream_name")
	val stream: String,
	val name: String,
	val created: String? = null,
	val config: ConsumerConfiguration,
	val delivered: SequenceInfo? = null,
	@SerialName("ack_floor")
	val ackFloor: SequenceInfo? = null,
	@SerialName("num_ack_pending")
	val numAckPending: Int = 0,
	@SerialName("num_redelivered")
	val numRedelivered: Int = 0,
	@SerialName("num_waiting")
	val numWaiting: Int = 0,
	@SerialName("num_pending")
	val numPending: Long = 0,
	val cluster: ClusterInfo? = null,
	@SerialName("push_bound")
	val pushBound: Boolean? = null,
	val paused: Boolean? = null,
) : JetStreamApiResponse

@Serializable
public data class ConsumerListResponse(
	val type: String? = null,
	val error: ApiError? = null,
	val total: Int = 0,
	val offset: Int = 0,
	val limit: Int = 0,
	val consumers: List<ConsumerInfo> = emptyList(),
)

@Serializable
public data class ConsumerNamesResponse(
	val type: String? = null,
	val error: ApiError? = null,
	val total: Int = 0,
	val offset: Int = 0,
	val limit: Int = 0,
	val consumers: List<String> = emptyList(),
)

@Serializable
public data class ConsumerPullRequest(
	val expires: Long? = null,
	val batch: Int? = null,
	@SerialName("no_wait")
	val noWait: Boolean? = null,
	@SerialName("max_bytes")
	val maxBytes: Int? = null,
	@SerialName("idle_heartbeat")
	val idleHeartbeat: Long? = null,
)

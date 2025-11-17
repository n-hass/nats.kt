package io.natskt.jetstream.api.consumer

import io.natskt.jetstream.api.AckPolicy
import io.natskt.jetstream.api.ConsumerConfig
import io.natskt.jetstream.api.DeliverPolicy
import io.natskt.jetstream.api.ReplayPolicy
import io.natskt.jetstream.internal.JetStreamDsl
import kotlin.time.Duration

@JetStreamDsl
public class ConsumerConfigurationBuilder internal constructor() {
	public var durableName: String? = null
	public var description: String? = null
	public var deliverSubject: String? = null
	public var deliverGroup: String? = null
	public var deliverPolicy: DeliverPolicy? = null
	public var optStartSequence: Long? = null
	public var optStartTime: String? = null
	public var ackPolicy: AckPolicy? = null
	public var ackWait: Duration? = null
	public var maxDeliver: Int? = null
	public var backoff: MutableList<Long>? = null
	public var filterSubject: String? = null
	public var filterSubjects: MutableList<String>? = null
	public var replayPolicy: ReplayPolicy? = null
	public var sampleFrequency: String? = null
	public var rateLimitBps: Long? = null
	public var maxAckPending: Int? = null
	public var maxWaiting: Int? = null
	public var maxBatch: Int? = null
	public var maxExpires: Long? = null
	public var maxBytes: Long? = null
	public var inactiveThreshold: Duration? = null
	public var numReplicas: Int? = null
	public var memoryStorage: Boolean? = null
	public var metadata: MutableMap<String, String>? = null
	public var headersOnly: Boolean? = null
	public var idleHeartbeat: Duration? = null
	public var flowControl: Boolean? = null
	public var direct: Boolean? = null
	public var maxPullWaiting: Int? = null
	public var maxRequestBatch: Int? = null
	public var maxRequestExpires: Long? = null
	public var maxRequestMaxBytes: Long? = null
	public var deliverMetrics: Boolean? = null

	public fun filterSubject(subject: String) {
		if (filterSubjects == null) {
			filterSubjects = mutableListOf()
		}
		filterSubjects!!.add(subject)
	}

	public fun backoff(delayInNanos: Long) {
		if (backoff == null) {
			backoff = mutableListOf()
		}
		backoff!!.add(delayInNanos)
	}
}

internal fun ConsumerConfigurationBuilder.build(): ConsumerConfig =
	ConsumerConfig(
		durableName = this.durableName,
		description = this.description,
		deliverSubject = this.deliverSubject,
		deliverGroup = this.deliverGroup,
		deliverPolicy = this.deliverPolicy,
		optStartSequence = this.optStartSequence,
		optStartTime = this.optStartTime,
		ackPolicy = this.ackPolicy,
		ackWait = this.ackWait,
		maxDeliver = this.maxDeliver,
		backoff = this.backoff?.toList(),
		filterSubject = this.filterSubject,
		filterSubjects = this.filterSubjects?.toList(),
		replayPolicy = this.replayPolicy,
		sampleFrequency = this.sampleFrequency,
		rateLimitBps = this.rateLimitBps,
		maxAckPending = this.maxAckPending,
		maxWaiting = this.maxWaiting,
		maxBatch = this.maxBatch,
		maxExpires = this.maxExpires,
		maxBytes = this.maxBytes,
		inactiveThreshold = this.inactiveThreshold,
		numReplicas = this.numReplicas,
		memoryStorage = this.memoryStorage,
		metadata = this.metadata?.toMap(),
		headersOnly = this.headersOnly,
		idleHeartbeat = this.idleHeartbeat,
		flowControl = this.flowControl,
		direct = this.direct,
		maxPullWaiting = this.maxPullWaiting,
		maxRequestBatch = this.maxRequestBatch,
		maxRequestExpires = this.maxRequestExpires,
		maxRequestMaxBytes = this.maxRequestMaxBytes,
		deliverMetrics = this.deliverMetrics,
	)

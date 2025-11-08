package io.natskt.jetstream.api.stream

import io.natskt.jetstream.api.DiscardPolicy
import io.natskt.jetstream.api.ExternalStream
import io.natskt.jetstream.api.RetentionPolicy
import io.natskt.jetstream.api.StorageType
import io.natskt.jetstream.api.StreamCompression
import io.natskt.jetstream.api.StreamConfiguration
import io.natskt.jetstream.api.StreamPlacement
import io.natskt.jetstream.api.StreamRepublish
import io.natskt.jetstream.api.StreamSource
import io.natskt.jetstream.api.SubjectTransform
import io.natskt.jetstream.internal.JetStreamDsl

@JetStreamDsl
public class ExternalStreamBuilder internal constructor() {
	public var apiPrefix: String? = null
	public var deliverPrefix: String? = null
}

internal fun ExternalStreamBuilder.build(): ExternalStream {
	val api = this.apiPrefix
	require(api != null) { "api must be set" }
	return ExternalStream(
		api = api,
		deliver = this.deliverPrefix,
	)
}

@JetStreamDsl
public class SubjectTransformBuilder internal constructor() {
	public var source: String? = null
	public var destination: String? = null
}

internal fun SubjectTransformBuilder.build(): SubjectTransform {
	val source = this.source
	val destination = this.destination
	require(source != null) { "source must be set" }
	require(destination != null) { "destination must be set" }
	return SubjectTransform(
		source = source,
		destination = destination,
	)
}

@JetStreamDsl
public class StreamSourceBuilder internal constructor(
	contextStreamName: String? = null,
) {
	public var name: String? = contextStreamName
	public var optStartSequence: Long? = null
	public var optStartTime: String? = null
	public var filterSubject: String? = null
	public var filterSubjects: MutableList<String>? = null
	public var external: ExternalStream? = null
	public var domain: String? = null
	public var subjectTransforms: MutableList<SubjectTransform>? = null

	public fun filterSubject(subject: String) {
		if (filterSubjects == null) {
			filterSubjects = mutableListOf()
		}
		filterSubjects!!.add(subject)
	}

	public fun external(builder: ExternalStreamBuilder.() -> Unit) {
		this.external = ExternalStreamBuilder().apply(builder).build()
	}

	public fun subjectTransform(builder: SubjectTransformBuilder.() -> Unit) {
		if (subjectTransforms == null) {
			subjectTransforms = mutableListOf()
		}
		subjectTransforms!!.add(SubjectTransformBuilder().apply(builder).build())
	}
}

internal fun StreamSourceBuilder.build(): StreamSource {
	val name = this.name
	require(name != null) { "StreamSource name must be provided" }

	return StreamSource(
		name = name,
		optStartSequence = this.optStartSequence,
		optStartTime = this.optStartTime,
		filterSubject = this.filterSubject,
		filterSubjects = this.filterSubjects?.toList(),
		external = this.external,
		domain = this.domain,
		subjectTransforms = this.subjectTransforms?.toList(),
	)
}

@JetStreamDsl
public class StreamPlacementBuilder internal constructor() {
	public var cluster: String? = null
	public var tags: MutableList<String>? = null
	public var domain: String? = null

	public fun tag(tag: String) {
		if (tags == null) {
			tags = mutableListOf()
		}
		tags!!.add(tag)
	}
}

internal fun StreamPlacementBuilder.build(): StreamPlacement =
	StreamPlacement(
		cluster = this.cluster,
		tags = this.tags?.toList(),
		domain = this.domain,
	)

@JetStreamDsl
public class StreamRepublishBuilder internal constructor() {
	public var source: String? = null
	public var destination: String? = null
	public var sourceKeepChars: Int? = null
	public var headers: Boolean? = null
}

internal fun StreamRepublishBuilder.build(): StreamRepublish {
	val source = this.source
	require(source != null) { "StreamRepublish source must be provided" }
	val destination = this.destination
	require(destination != null) { "StreamRepublish destination must be provided" }

	return StreamRepublish(
		source = source,
		destination = destination,
		sourceKeepChars = this.sourceKeepChars,
		headers = this.headers,
	)
}

@JetStreamDsl
public class StreamConfigurationBuilder internal constructor() {
	public var name: String? = null
	public var description: String? = null
	public var subjects: MutableList<String>? = null
	public var retention: RetentionPolicy? = null
	public var maxConsumers: Int? = null
	public var maxMessages: Long? = null
	public var maxMessagesPerSubject: Long? = null
	public var maxBytes: Long? = null
	public var maxAgeNanos: Long? = null
	public var maxMessageSize: Int? = null
	public var storage: StorageType? = null
	public var discard: DiscardPolicy? = null
	public var discardNewPerSubject: String? = null
	public var replicas: Int? = null
	public var noAck: Boolean? = null
	public var templateOwner: String? = null
	public var duplicateWindow: Long? = null
	public var placement: StreamPlacement? = null
	public var mirror: StreamSource? = null
	public var sources: MutableList<StreamSource>? = null
	public var allowRollupHeaders: Boolean? = null
	public var denyDelete: Boolean? = null
	public var denyPurge: Boolean? = null
	public var allowDirect: Boolean? = null
	public var mirrorDirect: Boolean? = null
	public var republish: StreamRepublish? = null
	public var sealed: Boolean? = null
	public var compression: StreamCompression? = null
	public var metadata: MutableMap<String, String>? = null

	public fun subject(subject: String) {
		if (subjects == null) {
			subjects = mutableListOf()
		}
		subjects!!.add(subject)
	}

	public fun placement(builder: StreamPlacementBuilder.() -> Unit) {
		placement = StreamPlacementBuilder().apply(builder).build()
	}

	public fun mirror(builder: StreamSourceBuilder.() -> Unit) {
		this.mirror = StreamSourceBuilder(contextStreamName = this.name).apply(builder).build()
	}

	public fun source(builder: StreamSourceBuilder.() -> Unit) {
		val source = StreamSourceBuilder(contextStreamName = this.name).apply(builder).build()
		if (sources == null) {
			sources = mutableListOf()
		}
		sources!!.add(source)
	}

	public fun republish(builder: StreamRepublishBuilder.() -> Unit) {
		republish = StreamRepublishBuilder().apply(builder).build()
	}
}

internal fun StreamConfigurationBuilder.build(): StreamConfiguration {
	val name = this.name
	require(name != null) { "Stream name must be provided" }

	return StreamConfiguration(
		name = name,
		description = this.description,
		subjects = this.subjects?.toList(),
		retention = this.retention,
		maxConsumers = this.maxConsumers,
		maxMessages = this.maxMessages,
		maxMessagesPerSubject = this.maxMessagesPerSubject,
		maxBytes = this.maxBytes,
		maxAgeNanos = this.maxAgeNanos,
		maxMessageSize = this.maxMessageSize,
		storage = this.storage,
		discard = this.discard,
		discardNewPerSubject = this.discardNewPerSubject,
		replicas = this.replicas,
		noAck = this.noAck,
		templateOwner = this.templateOwner,
		duplicateWindow = this.duplicateWindow,
		placement = this.placement,
		mirror = this.mirror,
		sources = this.sources?.toList(),
		allowRollupHeaders = this.allowRollupHeaders,
		denyDelete = this.denyDelete,
		denyPurge = this.denyPurge,
		allowDirect = this.allowDirect,
		mirrorDirect = this.mirrorDirect,
		republish = this.republish,
		sealed = this.sealed,
		compression = this.compression,
		metadata = this.metadata?.toMap(),
	)
}

package io.natskt.jetstream.api

public data class JetStreamMessageMetadata(
	val stream: String,
	val consumer: String,
	val streamSequence: Long,
	val consumerSequence: Long,
	val deliveryCount: Long?,
	val pending: Long?,
	val timestamp: String?,
)

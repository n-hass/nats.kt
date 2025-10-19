package io.natskt.jetstream.api.consumer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single message entry in a pull consumer response.
 */
@Serializable
public data class PullMessageEntry(
	val subject: String,
	val seq: Long,
	val data: String? = null, // Base64 encoded message data
	val headers: Map<String, List<String>>? = null,
	val timestamp: String? = null,
	@SerialName("stream")
	val streamName: String,
	@SerialName("consumer")
	val consumerName: String,
	@SerialName("stream_seq")
	val streamSequence: Long,
	@SerialName("consumer_seq")
	val consumerSequence: Long,
	@SerialName("pending")
	val pending: Long? = null,
	@SerialName("redelivered")
	val redeliveryCount: Long? = null,
)

package io.natskt.jetstream.api

import io.natskt.jetstream.api.consumer.PullMessageEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a response from a JetStream pull consumer operation.
 * This model is used to deserialize the JSON response from a CONSUMER.MSG.NEXT API call.
 */
@Serializable
public data class PullConsumerResponse(
	val type: String? = null,
	@SerialName("messages")
	val messages: List<PullMessageEntry> = emptyList(),
) : JetStreamApiResponse

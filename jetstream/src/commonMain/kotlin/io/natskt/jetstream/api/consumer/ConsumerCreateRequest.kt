package io.natskt.jetstream.api.consumer

import io.natskt.jetstream.api.ConsumerConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ConsumerCreateRequest(
	@SerialName("stream_name")
	val streamName: String,
	val config: ConsumerConfiguration,
	val action: ConsumerCreateAction,
)

@Serializable
internal enum class ConsumerCreateAction {
	@SerialName("create")
	Create,

	@SerialName("update")
	Update,

	@SerialName("")
	CreateOrUpdate,
}

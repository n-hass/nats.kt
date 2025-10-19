package io.natskt.jetstream.api.consumer

import io.natskt.jetstream.api.ConsumerConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ConsumerCreateRequest(
	val stream: String,
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

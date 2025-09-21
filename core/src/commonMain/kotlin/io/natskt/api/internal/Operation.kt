package io.natskt.api.internal

internal sealed interface Operation {
	data object Ok : Operation

	data class Err(
		val message: String?,
	) : Operation

	data object Ping : Operation, ClientOperation

	data object Pong : Operation, ClientOperation

	data object Empty : Operation
}

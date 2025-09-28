package io.natskt.internal

import io.natskt.api.internal.InternalNatsApi

@OptIn(InternalNatsApi::class)
sealed interface Operation : ParsedOutput {
	data object Ok : Operation

	data class Err(
		val message: String?,
	) : Operation

	data object Ping : Operation, ClientOperation

	data object Pong : Operation, ClientOperation

	data object Empty : Operation
}

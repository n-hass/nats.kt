package io.natskt.api.internal

internal sealed interface Operation {
    data object Ok : Operation

    data object Err : Operation

    data object Ping : Operation, ClientOperation

    data object Pong : Operation, ClientOperation
}

package io.natskt.internal.api

public sealed interface Operation

internal sealed interface ServerOperation : Operation

internal sealed interface ClientOperation : Operation

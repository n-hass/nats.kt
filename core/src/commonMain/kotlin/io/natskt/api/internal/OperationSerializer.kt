package io.natskt.api.internal

internal interface OperationSerializer {
    fun parseOrNull(line: String): Operation?

    fun encode(op: ClientOperation): String
}

package io.natskt.api.internal

import io.ktor.utils.io.ByteReadChannel

internal interface OperationSerializer {
	suspend fun parse(channel: ByteReadChannel): Operation?

	fun encode(op: ClientOperation): ByteArray
}

internal const val DEFAULT_MAX_LINE_BYTES: Long = 1024 * 1024 * 5 // 5 KB

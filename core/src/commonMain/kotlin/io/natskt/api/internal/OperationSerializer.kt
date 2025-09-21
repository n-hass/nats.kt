package io.natskt.api.internal

import io.ktor.utils.io.ByteReadChannel

internal interface OperationSerializer {
	suspend fun parse(channel: ByteReadChannel): Operation?

	fun encode(op: ClientOperation): ByteArray
}

/**
 * The max line bytes
 */
internal const val DEFAULT_MAX_CONTROL_LINE_BYTES: Int = 1024 // 1 KB
internal const val DEFAULT_MAX_PAYLOAD_BYTES: Int = 52_428_800 // 50 MB

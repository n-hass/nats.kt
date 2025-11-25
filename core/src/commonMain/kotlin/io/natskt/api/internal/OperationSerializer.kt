package io.natskt.api.internal

import io.ktor.utils.io.ByteReadChannel
import io.natskt.internal.ClientOperation
import io.natskt.internal.ParsedOutput

internal interface OperationEncodeBuffer {
	suspend fun writeByte(value: Byte)

	suspend fun writeBytes(
		value: ByteArray,
		offset: Int = 0,
		length: Int = value.size,
	)

	suspend fun writeAscii(value: String)

	suspend fun writeInt(value: Int)

	suspend fun writeCrLf()
}

@OptIn(InternalNatsApi::class)
internal interface OperationSerializer {
	suspend fun parse(channel: ByteReadChannel): ParsedOutput?

	suspend fun encode(
		op: ClientOperation,
		buffer: OperationEncodeBuffer,
	)
}

/**
 * The max line bytes
 */
internal const val DEFAULT_MAX_CONTROL_LINE_BYTES: Int = 1024 // 1 KB
internal const val DEFAULT_MAX_PAYLOAD_BYTES: Int = 52_428_800 // 50 MB

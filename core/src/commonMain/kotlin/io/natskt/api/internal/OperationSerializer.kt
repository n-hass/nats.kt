package io.natskt.api.internal

import io.ktor.utils.io.ByteReadChannel
import io.natskt.internal.ClientOperation
import io.natskt.internal.ParsedOutput

@OptIn(InternalNatsApi::class)
internal interface OperationSerializer {
	suspend fun parse(channel: ByteReadChannel): ParsedOutput?

	suspend fun encode(
		op: ClientOperation,
		buffer: OperationEncodeBuffer,
	)
}

internal const val DEFAULT_MAX_CONTROL_LINE_BYTES: Int = 4 * 1024 // 4 KB
internal const val DEFAULT_MAX_PAYLOAD_BYTES: Int = 50 * 1024 * 1024 // 50 MB

package io.natskt.api.internal

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

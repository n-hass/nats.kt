package io.natskt.tls

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

public class NativeTlsConnection internal constructor(
	public val input: ByteReadChannel,
	public val output: ByteWriteChannel,
	private val closer: suspend () -> Unit,
) {
	public suspend fun close(): Unit = closer()
}

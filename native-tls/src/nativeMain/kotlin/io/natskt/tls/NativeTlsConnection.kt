package io.natskt.tls

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.natskt.tls.internal.TlsHandshake

public class NativeTlsConnection internal constructor(
	private val handshake: TlsHandshake,
) {
	public val input: ByteReadChannel get() = handshake.appDataInput
	public val output: ByteWriteChannel get() = handshake.appDataOutput

	public suspend fun close() {
		handshake.closeGracefully()
	}
}

package io.natskt.client.transport

import io.ktor.network.sockets.Connection
import io.ktor.network.sockets.awaitClosed
import io.ktor.network.sockets.isClosed
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.natskt.tls.NativeTlsConnection
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

internal class NativeTlsTransportAdapter(
	private val rawConnection: Connection,
	private val tls: NativeTlsConnection,
	override val coroutineContext: CoroutineContext,
) : Transport,
	CoroutineScope {
	override val isClosed: Boolean get() = rawConnection.socket.isClosed

	override val incoming: ByteReadChannel get() = tls.input

	override suspend fun close() {
		tls.close()
		rawConnection.socket.close()
		rawConnection.socket.awaitClosed()
	}

	override suspend fun upgradeTLS(): Transport = this

	override suspend fun write(block: suspend (ByteWriteChannel) -> Unit) {
		block(tls.output)
	}

	override suspend fun flush() {
		tls.output.flush()
	}
}

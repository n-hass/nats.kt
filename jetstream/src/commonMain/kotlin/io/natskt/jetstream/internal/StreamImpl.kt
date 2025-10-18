package io.natskt.jetstream.internal

import io.natskt.api.internal.InternalNatsApi
import io.natskt.jetstream.api.Stream
import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.client.JetStreamClientImpl
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(InternalNatsApi::class)
internal class StreamImpl(
	private val name: String,
	private val client: JetStreamClientImpl,
	initialInfo: StreamInfo?,
) : Stream {
	@OptIn(InternalNatsApi::class)
	override val info = MutableStateFlow<StreamInfo?>(initialInfo)

	override suspend fun updateStreamInfo(): Result<StreamInfo> {
		val new = client.getStreamInfo(name)
		new.onSuccess {
			info.value = it
		}
		return new
	}
}

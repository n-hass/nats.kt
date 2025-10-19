package io.natskt.jetstream.api.consumer

import io.natskt.jetstream.api.ConsumerInfo
import kotlinx.coroutines.flow.StateFlow

public interface Consumer {
	public val info: StateFlow<ConsumerInfo?>

	public suspend fun updateConsumerInfo(): Result<ConsumerInfo>
}

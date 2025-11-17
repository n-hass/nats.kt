package io.natskt.jetstream.api.consumer

import io.natskt.jetstream.api.ConsumerInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a [PullConsumer] or [PushConsumer].
 * You should down cast to operate based on the type of consumer
 */
public sealed interface Consumer : AutoCloseable {
	public val info: StateFlow<ConsumerInfo?>

	public suspend fun updateConsumerInfo(): Result<ConsumerInfo>
}

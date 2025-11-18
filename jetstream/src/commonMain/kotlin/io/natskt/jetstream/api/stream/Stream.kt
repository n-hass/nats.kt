package io.natskt.jetstream.api.stream

import io.natskt.jetstream.api.StreamInfo
import io.natskt.jetstream.api.consumer.ConsumerConfigurationBuilder
import io.natskt.jetstream.api.consumer.PullConsumer
import io.natskt.jetstream.api.consumer.PushConsumer
import kotlinx.coroutines.flow.StateFlow

public interface Stream {
	/**
	 * The last fetched [io.natskt.jetstream.api.StreamInfo], or null if it has not been fetched yet.
	 * On creation of the Stream object, an initial fetch will be triggered, so this value will eventually be non-null.
	 */
	public val info: StateFlow<StreamInfo?>

	/**
	 * Requests the latest stream info. Future access to [info] will return this new updated value.
	 */
	public suspend fun updateStreamInfo(): Result<StreamInfo>

	/**
	 * Bind a consumer with the given name
	 */
	public suspend fun pullConsumer(name: String): PullConsumer

	/**
	 * Bind a consumer with the given name
	 */
	public suspend fun pushConsumer(name: String): PushConsumer

	/**
	 * Create a new [PullConsumer] with the given configuration
	 */
	public suspend fun createPullConsumer(configure: ConsumerConfigurationBuilder.() -> Unit): PullConsumer

	/**
	 * Create a new [PushConsumer] with the given configuration
	 */
	public suspend fun createPushConsumer(configure: ConsumerConfigurationBuilder.() -> Unit): PushConsumer
}

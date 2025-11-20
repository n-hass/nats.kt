package io.natskt.jetstream.api.consumer

import io.natskt.jetstream.api.ConsumerConfig

public sealed class SubscribeOptions private constructor() {
	public abstract val streamName: String
	public abstract val manualAck: Boolean
	public abstract val skipConsumerInfo: Boolean

	/**
	 * Calls `CONSUMER.INFO` API to get the configuration of a consumer,
	 * then binds to it for use.
	 */
	public data class Attach(
		public override val streamName: String,
		public val consumerName: String,
		public override val manualAck: Boolean = false,
	) : SubscribeOptions() {
		public override val skipConsumerInfo: Boolean = false
	}

	/**
	 * Call the management API to create or update a consumer
	 * with the given configuration.
	 */
	public data class CreateOrUpdate(
		public override val streamName: String,
		public val consumerName: String,
		public val config: ConsumerConfig,
		public override val manualAck: Boolean = false,
		public override val skipConsumerInfo: Boolean = false,
	) : SubscribeOptions()
}

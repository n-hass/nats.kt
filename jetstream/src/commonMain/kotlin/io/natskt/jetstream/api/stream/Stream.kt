package io.natskt.jetstream.api.stream

import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.MessageGetRequest
import io.natskt.jetstream.api.StoredMessage
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
	 * Create a new [PullConsumer] with the given configuration
	 */
	public suspend fun createPullConsumer(configure: ConsumerConfigurationBuilder.() -> Unit): PullConsumer

	/**
	 * Create a new [PushConsumer] with the given configuration
	 */
	public suspend fun createPushConsumer(configure: ConsumerConfigurationBuilder.() -> Unit): PushConsumer

	/**
	 * Updates an existing consumer. Must already exist.
	 * @param configure configuration builder for the consumer
	 * @return consumer information
	 */
	public suspend fun updateConsumer(configure: ConsumerConfigurationBuilder.() -> Unit): ConsumerInfo

	/**
	 * Get info for a consumer on this stream
	 */
	public suspend fun getConsumerInfo(name: String): ConsumerInfo

	/**
	 * Delete a consumer from this stream
	 */
	public suspend fun deleteConsumer(name: String): Boolean

	/**
	 * List consumers of this stream by name
	 */
	public suspend fun getConsumerNames(): List<String>

	/**
	 * Get all consumers on this stream
	 */
	public suspend fun getConsumers(): List<ConsumerInfo>

	/**
	 * Get message by sequence number.
	 * @param sequence the message sequence number
	 * @return message information
	 */
	public suspend fun getMessage(sequence: ULong): StoredMessage

	/**
	 * Get message by request.
	 * @param request the message get request
	 * @return message information
	 */
	public suspend fun getMessage(request: MessageGetRequest): StoredMessage

	/**
	 * Get the last message for a subject.
	 * @param subject the subject
	 * @return message information
	 */
	public suspend fun getLastMessage(subject: String): StoredMessage

	/**
	 * Get the next message with sequence >= the given sequence for a subject.
	 * @param sequence the minimum sequence number
	 * @param subject the subject
	 * @return message information
	 */
	public suspend fun getNextMessage(
		sequence: ULong,
		subject: String,
	): StoredMessage

	/**
	 * Deletes a message, optionally erasing its content.
	 * @param sequence the message sequence number
	 * @param erase whether to erase the message content
	 * @return true if the delete succeeded
	 */
	public suspend fun deleteMessage(
		sequence: ULong,
		erase: Boolean = false,
	): Boolean
}

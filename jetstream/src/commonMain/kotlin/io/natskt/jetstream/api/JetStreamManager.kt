package io.natskt.jetstream.api

import io.natskt.jetstream.api.consumer.ConsumerConfigurationBuilder
import io.natskt.jetstream.api.stream.Stream
import io.natskt.jetstream.api.stream.StreamConfigurationBuilder
import kotlin.time.Instant

public interface JetStreamManager {
	// Account Operations

	/**
	 * Gets the account statistics for the logged in account.
	 */
	public suspend fun getAccountStatistics(): AccountInfo

	// Stream Operations

	/**
	 * Create a new stream
	 */
	public suspend fun createStream(configure: StreamConfigurationBuilder.() -> Unit): Stream

	/**
	 * Updates an existing stream configuration.
	 * @param streamName the name of the stream to update
	 * @param configure configuration builder for the stream
	 * @return updated stream information
	 */
	public suspend fun updateStream(
		streamName: String,
		configure: StreamConfigurationBuilder.() -> Unit,
	): StreamInfo

	/**
	 * Deletes an existing stream.
	 * @param streamName the stream name to delete
	 * @return true if the delete succeeded
	 */
	public suspend fun deleteStream(streamName: String): Boolean

	/**
	 * Gets the info for an existing stream.
	 * @param streamName the stream name
	 * @param options the stream info options to include additional data
	 * @return stream information
	 */
	public suspend fun getStreamInfo(
		streamName: String,
		options: StreamInfoOptions? = null,
	): StreamInfo

	/**
	 * Purge all messages from a stream.
	 * @param streamName the stream name
	 * @return purge response with count of purged messages
	 */
	public suspend fun purgeStream(streamName: String): PurgeResponse

	/**
	 * Purge messages from a stream with options.
	 * @param streamName the stream name
	 * @param options purge options to filter which messages to purge
	 * @return purge response with count of purged messages
	 */
	public suspend fun purgeStream(
		streamName: String,
		options: PurgeOptions,
	): PurgeResponse

	/**
	 * Get stream names that have subjects matching the subject filter.
	 * @param subjectFilter the subject filter (wildcards allowed)
	 * @return list of matching stream names
	 */
	public suspend fun getStreamNames(subjectFilter: String? = null): List<String>

	/**
	 * Get streams that have subjects matching the subject filter.
	 * @param subjectFilter the subject filter (wildcards allowed)
	 * @return list of matching stream information objects
	 */
	public suspend fun getStreams(subjectFilter: String? = null): List<StreamInfo>

	// Consumer Operations

	/**
	 * Creates or updates a consumer.
	 * @param streamName the stream name
	 * @param configure configuration builder for the consumer
	 * @return consumer information
	 */
	public suspend fun createOrUpdateConsumer(
		streamName: String,
		configure: ConsumerConfigurationBuilder.() -> Unit,
	): ConsumerInfo

	/**
	 * Creates a consumer. Must not already exist.
	 * @param streamName the stream name
	 * @param configure configuration builder for the consumer
	 * @return consumer information
	 */
	public suspend fun createConsumer(
		streamName: String,
		configure: ConsumerConfigurationBuilder.() -> Unit,
	): ConsumerInfo

	/**
	 * Updates an existing consumer. Must already exist.
	 * @param streamName the stream name
	 * @param configure configuration builder for the consumer
	 * @return consumer information
	 */
	public suspend fun updateConsumer(
		streamName: String,
		configure: ConsumerConfigurationBuilder.() -> Unit,
	): ConsumerInfo

	/**
	 * Deletes a consumer.
	 * @param streamName the stream name
	 * @param consumerName the consumer name
	 * @return true if the delete succeeded
	 */
	public suspend fun deleteConsumer(
		streamName: String,
		consumerName: String,
	): Boolean

	/**
	 * Pauses a consumer until the specified time.
	 * @param streamName the stream name
	 * @param consumerName the consumer name
	 * @param pauseUntil the time until which the consumer should be paused (RFC3339 format)
	 * @return consumer pause response
	 */
	public suspend fun pauseConsumer(
		streamName: String,
		consumerName: String,
		pauseUntil: Instant,
	): ConsumerPauseResponse

	/**
	 * Resumes a paused consumer.
	 * @param streamName the stream name
	 * @param consumerName the consumer name
	 * @return true if the resume succeeded
	 */
	public suspend fun resumeConsumer(
		streamName: String,
		consumerName: String,
	): Boolean

	/**
	 * Gets the info for an existing consumer.
	 * @param streamName the stream name
	 * @param consumerName the consumer name
	 * @return consumer information
	 */
	public suspend fun getConsumerInfo(
		streamName: String,
		consumerName: String,
	): ConsumerInfo

	/**
	 * Get all consumer names for a stream.
	 * @param streamName the stream name
	 * @return list of consumer names
	 */
	public suspend fun getConsumerNames(streamName: String): List<String>

	/**
	 * Get all consumers for a stream.
	 * @param streamName the stream name
	 * @return list of consumer information objects
	 */
	public suspend fun getConsumers(streamName: String): List<ConsumerInfo>

	// Message Operations

	/**
	 * Get message info by sequence number.
	 * @param streamName the stream name
	 * @param sequence the message sequence number
	 * @param direct use the direct-get API, which must be supported by the server
	 * @return message information
	 */
	public suspend fun getMessage(
		streamName: String,
		sequence: ULong,
		direct: Boolean = false,
	): StoredMessage

	/**
	 * Get message info by request.
	 * @param streamName the stream name
	 * @param request the message get request
	 * @param direct use the direct-get API, which must be supported by the server
	 * @return message information
	 */
	public suspend fun getMessage(
		streamName: String,
		request: MessageGetRequest,
		direct: Boolean = false,
	): StoredMessage

	/**
	 * Get the last message for a subject.
	 * @param streamName the stream name
	 * @param subject the subject
	 * @param direct use the direct-get API, which must be supported by the server
	 * @return message information
	 */
	public suspend fun getLastMessage(
		streamName: String,
		subject: String,
		direct: Boolean = false,
	): StoredMessage

	/**
	 * Get the next message with sequence >= the given sequence for a subject.
	 * @param streamName the stream name
	 * @param sequence the minimum sequence number
	 * @param subject the subject
	 * @param direct use the direct-get API, which must be supported by the server
	 * @return message information
	 */
	public suspend fun getNextMessage(
		streamName: String,
		sequence: ULong,
		subject: String,
		direct: Boolean = true,
	): StoredMessage

	/**
	 * Deletes a message, optionally erasing its content.
	 * @param streamName the stream name
	 * @param sequence the message sequence number
	 * @param erase whether to erase the message content
	 * @return true if the delete succeeded
	 */
	public suspend fun deleteMessage(
		streamName: String,
		sequence: ULong,
		erase: Boolean = false,
	): Boolean
}

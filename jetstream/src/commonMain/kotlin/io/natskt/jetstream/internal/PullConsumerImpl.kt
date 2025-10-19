package io.natskt.jetstream.internal

import io.natskt.api.JetStreamMessage
import io.natskt.internal.wireJsonFormat
import io.natskt.jetstream.api.ApiError
import io.natskt.jetstream.api.ConsumerInfo
import io.natskt.jetstream.api.ConsumerPullRequest
import io.natskt.jetstream.api.JetStreamUnknownResponseException
import io.natskt.jetstream.api.PullConsumerResponse
import io.natskt.jetstream.api.consumer.PullConsumer
import io.natskt.jetstream.api.internal.decodeApiResponse
import io.natskt.jetstream.client.JetStreamClientImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.collections.emptyList
import kotlin.time.Duration

internal class PullConsumerImpl(
	val name: String,
	val streamName: String,
	private val client: JetStreamClientImpl,
	private val defaultTimeout: Long = 10_000_000_000, // 10 seconds in nanoseconds
	initialInfo: ConsumerInfo?,
) : PullConsumer {
	private var lastPullCode = 0
	private var lastPullBody = ""

	override val info = MutableStateFlow(initialInfo)

	override suspend fun updateConsumerInfo(): Result<ConsumerInfo> {
		val new = client.getConsumerInfo(streamName, name)
		new.onSuccess {
			info.value = it
		}
		return new
	}

	override suspend fun fetch(
		batch: Int,
		expires: Duration?,
		noWait: Boolean?,
		maxBytes: Int?,
		heartbeat: Duration?,
	): List<JetStreamMessage> {
		val code = batch.hashCode() + expires.hashCode() + noWait.hashCode() + maxBytes.hashCode() + heartbeat.hashCode()
		val body =
			if (code == lastPullCode) {
				lastPullBody
			} else {
				val body =
					wireJsonFormat.encodeToString(
						ConsumerPullRequest(
							expires = expires?.inWholeNanoseconds ?: (if (noWait == true) null else defaultTimeout),
							batch = batch,
							noWait = noWait,
							maxBytes = maxBytes,
							idleHeartbeat = heartbeat?.inWholeNanoseconds,
						),
					)
				lastPullCode = code
				lastPullBody = body
				body
			}

		val response = client.pull(streamName, name, body)
		val data = response?.data
		if (data == null || data.isEmpty()) {
			return emptyList()
		}

		val apiResponse = wireJsonFormat.decodeApiResponse<PullConsumerResponse>(data.decodeToString())

		return when (apiResponse) {
			is PullConsumerResponse ->
				buildList<JetStreamMessage> {
					apiResponse.messages.forEach {
						println("got: $it")
					}
				}
			is ApiError -> emptyList()
			else -> throw JetStreamUnknownResponseException(apiResponse)
		}
	}
}

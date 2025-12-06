package io.natskt.jetstream.internal

import io.natskt.api.Message

public interface CanRequest {
	public val context: JetStreamContext

	public suspend fun request(
		subject: String,
		message: String?,
		headers: Map<String, List<String>>? = null,
		timeoutMs: Long = 5_000,
	): Message
}

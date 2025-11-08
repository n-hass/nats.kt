package io.natskt.jetstream.internal

import io.natskt.api.Message
import io.natskt.jetstream.client.JetStreamConfiguration

public interface CanRequest {
	public val config: JetStreamConfiguration

	public suspend fun request(
		subject: String,
		message: String?,
		headers: Map<String, List<String>>? = null,
		timeoutMs: Long = 5000,
	): Message
}

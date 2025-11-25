package io.natskt.jetstream.integration

import harness.RemoteNatsServer
import io.ktor.utils.io.ClosedWriteChannelException
import io.natskt.NatsClient
import io.natskt.jetstream.JetStreamClient
import kotlin.time.Duration.Companion.seconds
import io.natskt.api.NatsClient as ApiNatsClient
import io.natskt.jetstream.api.JetStreamClient as ApiJetStreamClient

internal suspend fun <T> withJetStreamClient(
	server: RemoteNatsServer,
	block: suspend (ApiNatsClient, ApiJetStreamClient) -> T,
): T {
	val client =
		NatsClient {
			this.server = server.uri
			connectTimeout = 10.seconds
			maxReconnects = 3
		}
	client.connect().getOrThrow()
	val js = JetStreamClient(client)
	return try {
		block(client, js)
	} finally {
		runCatching { client.drain(1.seconds) }
		runCatching { client.disconnect() }
	}
}

internal suspend fun ignoreClosedWrite(block: suspend () -> Unit) {
	try {
		block()
	} catch (_: ClosedWriteChannelException) {
		// connection already gone; nothing to clean up
	}
}

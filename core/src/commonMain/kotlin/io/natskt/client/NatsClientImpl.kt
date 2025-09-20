package io.natskt.client

import io.natskt.api.NatsClient
import io.natskt.api.Subscription
import io.natskt.client.connection.ConnectionManager
import io.natskt.internal.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

internal class NatsClientImpl(
	val configuration: ClientConfiguration,
) : NatsClient {
	internal val connectionManager = ConnectionManager(configuration)

	override val subscriptions: Map<String, Subscription>
		get() = TODO("Not yet implemented")

	override suspend fun connect() {
		connectionManager.start()
		CoroutineScope(currentCoroutineContext()).launch {
			connectionManager.connectionStatus.collect {
				println("Connection status change: $it")
			}
		}
	}

	override suspend fun subscribe(
		subject: Subject,
		queueGroup: String?,
	): Subscription {
		TODO("Not yet implemented")
	}

	override suspend fun subscribe(
		id: String,
		subject: Subject,
		queueGroup: String?,
	): Subscription {
		TODO("Not yet implemented")
	}
}

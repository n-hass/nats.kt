package io.natskt

import io.natskt.api.NatsClient
import io.natskt.client.ClientConfigurationBuilder
import io.natskt.client.build
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
public fun NatsClient(block: ClientConfigurationBuilder.() -> Unit): NatsClient {
	contract {
		callsInPlace(block, InvocationKind.EXACTLY_ONCE)
	}

	val config =
		ClientConfigurationBuilder()
			.apply(block)
			.build()

	val client = NatsClient(config)

	return client
}

@OptIn(ExperimentalContracts::class)
public fun NatsClient(uri: String): NatsClient {
	val config =
		ClientConfigurationBuilder()
			.apply {
				server = uri
			}.build()

	val client = NatsClient(config)

	return client
}

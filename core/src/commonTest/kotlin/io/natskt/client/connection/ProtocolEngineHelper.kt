package io.natskt.client.connection

import io.ktor.http.Url
import io.ktor.util.collections.ConcurrentMap
import io.ktor.utils.io.ByteReadChannel
import io.natskt.api.Credentials
import io.natskt.api.internal.OperationEncodeBuffer
import io.natskt.api.internal.OperationSerializer
import io.natskt.client.NatsServerAddress
import io.natskt.client.transport.Transport
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.ClientOperation
import io.natskt.internal.ParsedOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal fun engine(
	url: String = "nats://localhost:4222",
	credentials: Credentials? = null,
): ProtocolEngineImpl =
	ProtocolEngineImpl(
		transportFactory = FakeTransportFactory,
		address = NatsServerAddress(Url(url)),
		parser = NoopSerializer,
		subscriptions = emptyMap(),
		pendingRequests = ConcurrentMap(),
		serverInfo = MutableStateFlow(null),
		credentials = credentials,
		tlsRequired = false,
		operationBufferCapacity = 32,
		writeBufferLimitBytes = 64 * 1024,
		writeFlushIntervalMs = 5,
		scope =
			object : CoroutineScope {
				override val coroutineContext: CoroutineContext = EmptyCoroutineContext
			},
	)

internal object FakeTransportFactory : TransportFactory {
	override suspend fun connect(
		address: NatsServerAddress,
		context: CoroutineContext,
	): Transport = throw UnsupportedOperationException("not used in test")
}

internal object NoopSerializer : OperationSerializer {
	override suspend fun parse(channel: ByteReadChannel): ParsedOutput? = null

	override suspend fun encode(
		op: ClientOperation,
		buffer: OperationEncodeBuffer,
	) { }
}

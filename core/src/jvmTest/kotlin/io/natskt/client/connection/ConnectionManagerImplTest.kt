package io.natskt.client.connection

import io.ktor.http.Url
import io.ktor.util.collections.ConcurrentMap
import io.natskt.api.internal.DEFAULT_MAX_PAYLOAD_BYTES
import io.natskt.client.ClientConfiguration
import io.natskt.client.NatsServerAddress
import io.natskt.client.transport.Transport
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.InternalSubscriptionHandler
import io.natskt.internal.NUID
import io.natskt.internal.PendingRequest
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionManagerImplTest {
	@Test
	fun `selectAddress skips lame duck server when another is available`() {
		val manager = manager("nats://a:4222", "nats://b:4222")
		val first = manager.config.servers[0]
		val second = manager.config.servers[1]

		manager.markLameDuck(first, timestamp = 0)
		val selected = manager.selectAddress(now = LAME_DUCK_BACKOFF_MILLIS / 2)

		assertEquals(second, selected)
	}

	@Test
	fun `selectAddress falls back to lame duck server when it is the only option`() {
		val manager = manager("nats://solo:4222")
		val only = manager.config.servers[0]

		manager.markLameDuck(only, timestamp = 0)
		val selected = manager.selectAddress(now = 10)

		assertEquals(only, selected)
	}

	@Test
	fun `lame duck mark expires after backoff window`() {
		val manager = manager("nats://x:4222", "nats://y:4222")
		val first = manager.config.servers[0]
		val second = manager.config.servers[1]

		manager.markLameDuck(first, timestamp = 0)
		repeat(5) {
			val selected = manager.selectAddress(now = LAME_DUCK_BACKOFF_MILLIS + Random.nextLong(1, 1000))
			assertTrue(selected == first || selected == second)
		}
	}

	private fun manager(vararg urls: String): ConnectionManagerImpl {
		val addresses = urls.map { NatsServerAddress(Url(it)) }
		val config =
			ClientConfiguration(
				servers = addresses,
				transportFactory = NoopTransportFactory,
				credentials = null,
				inboxPrefix = "_INBOX.",
				maxReconnects = null,
				connectTimeoutMs = 1000,
				reconnectDebounceMs = 1000,
				maxControlLineBytes = 1024,
				writeBufferLimitBytes = 64 * 1024,
				writeFlushIntervalMs = 5,
				tlsRequired = false,
				nuid = NUID.Default,
				scope = CoroutineScope(EmptyCoroutineContext),
				maxParallelRequests = null,
				ownsScope = false,
				maxPayloadBytes = DEFAULT_MAX_PAYLOAD_BYTES,
			)
		return ConnectionManagerImpl(
			config,
			ConcurrentMap<String, InternalSubscriptionHandler>(),
			ConcurrentMap<String, PendingRequest>(),
		)
	}

	private object NoopTransportFactory : TransportFactory {
		override suspend fun connect(
			address: NatsServerAddress,
			context: CoroutineContext,
		): Transport = throw UnsupportedOperationException("not used")
	}
}

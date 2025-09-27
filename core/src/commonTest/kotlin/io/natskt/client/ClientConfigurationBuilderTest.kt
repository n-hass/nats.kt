package io.natskt.client

import io.natskt.client.transport.Transport
import io.natskt.client.transport.TransportFactory
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class RecordingTransportFactory : TransportFactory {
	override suspend fun connect(
		address: NatsServerAddress,
		context: CoroutineContext,
	): Transport {
		error("not used in tests")
	}
}

class ClientConfigurationBuilderTest {
	@Test
	fun `build requires at least one server`() {
		val builder = ClientConfigurationBuilder()

		val error =
			assertFailsWith<IllegalStateException> {
				builder.build()
			}

		assertTrue(error.message!!.contains("must provide at least one server"))
	}

	@Test
	fun `build merges singular and plural server definitions`() {
		val config =
			ClientConfigurationBuilder()
				.apply {
					server = "nats://localhost:4222"
					servers = listOf("nats://localhost:5222", "nats://localhost:6222")
				}.build()

		val urls = config.servers.map { it.url.toString() }
		assertEquals(listOf("nats://localhost:5222", "nats://localhost:6222", "nats://localhost:4222"), urls)
	}

	@Test
	fun `build normalises inbox prefix and exposes inbox length`() {
		val config =
			ClientConfigurationBuilder()
				.apply {
					server = "nats://example.com:4222"
					inboxPrefix = "_custom"
				}.build()

		assertEquals("_custom.", config.inboxPrefix)
		assertEquals(config.inboxPrefix.length + 22, config.inboxLength)
		assertTrue(config.createInbox().startsWith(config.inboxPrefix))
	}

	@Test
	fun `build honours custom transport factory`() {
		val customTransport = RecordingTransportFactory()

		val config =
			ClientConfigurationBuilder()
				.apply {
					server = "nats://example.com:4222"
					this.transport = customTransport
				}.build()

		assertSame(customTransport, config.transportFactory)
	}
}

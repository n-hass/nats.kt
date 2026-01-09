package io.natskt.client.connection

import io.natskt.api.Credentials
import io.natskt.internal.ServerOperation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProtocolEngineImplUrlAuthTest {
	@Test
	fun `uses url credentials when none are provided`() {
		val engine = engine(url = "nats://alice:secret@localhost:4222")
		val connect = engine.buildConnectOp(defaultInfo())
		assertEquals("alice", connect.user)
		assertEquals("secret", connect.pass)
	}

	@Test
	fun `explicit password credentials override url credentials`() {
		val creds = Credentials.Password(username = "carol", password = "topsecret")
		val engine = engine(url = "nats://alice:secret@localhost:4222", credentials = creds)
		val connect = engine.buildConnectOp(defaultInfo())
		assertEquals("carol", connect.user)
		assertEquals("topsecret", connect.pass)
	}

	@Test
	fun `blank explicit credentials fall back to url credentials`() {
		val creds = Credentials.Password(username = "", password = "")
		val engine = engine(url = "nats://bob:password@localhost:4222", credentials = creds)
		val connect = engine.buildConnectOp(defaultInfo())
		assertEquals("bob", connect.user)
		assertEquals("password", connect.pass)
	}

	@Test
	fun `url username is preserved when password is missing`() {
		val engine = engine(url = "nats://solo@localhost:4222")
		val connect = engine.buildConnectOp(defaultInfo())
		assertEquals("solo", connect.user)
		assertNull(connect.pass)
	}

	private fun defaultInfo(): ServerOperation.InfoOp =
		ServerOperation.InfoOp(
			serverId = "test",
			serverName = "srv",
			version = "1",
			go = "go",
			host = "localhost",
			port = 4222,
			headers = true,
			maxPayload = 1024,
			proto = 1,
			clientId = null,
			authRequired = true,
			tlsRequired = null,
			tlsVerify = null,
			tlsAvailable = null,
			connectUrls = null,
			wsConnectUrls = null,
			ldm = null,
			gitCommit = null,
			jetstream = null,
			ip = null,
			clientIp = null,
			nonce = null,
			cluster = null,
			domain = null,
			xkey = null,
		)
}

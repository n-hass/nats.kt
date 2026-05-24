package integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.NatsClient
import io.natskt.api.internal.InternalNatsApi
import io.natskt.client.transport.TcpTransport
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MutualTlsIntegrationTest {
	@OptIn(InternalNatsApi::class)
	@Test
	fun `mTLS connects when client cert is supplied`() =
		RemoteNatsHarness.runBlocking(enableTls = true, tlsRequireClientCert = true) { server ->
			val serverCertPem = assertNotNull(server.tlsServerCertPem)
			val clientCertPem = assertNotNull(server.tlsClientCertPem)
			val clientKeyPem = assertNotNull(server.tlsClientKeyPem)

			val client =
				NatsClient {
					this.server = server.tlsUri!!
					transport = TcpTransport
					tls {
						caCertificates(serverCertPem)
						clientCertificate(clientCertPem, clientKeyPem)
					}
					maxReconnects = 1
				}
			val result = client.connect()
			assertTrue(result.isSuccess, "mTLS connect failed: ${result.exceptionOrNull()}")
			client.disconnect()
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `mTLS connects when client cert is supplied and using tls-first`() =
		RemoteNatsHarness.runBlocking(enableTls = true, tlsHandshakeFirst = true, tlsRequireClientCert = true) { server ->
			val serverCertPem = assertNotNull(server.tlsServerCertPem)
			val clientCertPem = assertNotNull(server.tlsClientCertPem)
			val clientKeyPem = assertNotNull(server.tlsClientKeyPem)

			val client =
				NatsClient {
					this.server = server.tlsUri!!
					transport = TcpTransport
					tls {
						caCertificates(serverCertPem)
						clientCertificate(clientCertPem, clientKeyPem)
						tlsFirst = true
					}
					maxReconnects = 1
				}
			try {
				val result = client.connect()
				assertTrue(result.isSuccess, "mTLS connect failed: ${result.exceptionOrNull()}")
			} finally {
				client.disconnect()
			}
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `mTLS server rejects connection without client cert`() =
		RemoteNatsHarness.runBlocking(enableTls = true, tlsRequireClientCert = true) { server ->
			val serverCertPem = assertNotNull(server.tlsServerCertPem)
			val client =
				NatsClient {
					this.server = server.tlsUri!!
					transport = TcpTransport
					tls { caCertificates(serverCertPem) }
					maxReconnects = 1
				}
			try {
				val result = client.connect()
				assertTrue(result.isFailure, "expected handshake to fail without client cert, but got: ${result.getOrNull()}")
			} finally {
				try {
					client.disconnect()
				} catch (_: Throwable) {
				}
			}
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `mTLS server rejects connection without client cert when using tls-first`() =
		RemoteNatsHarness.runBlocking(enableTls = true, tlsHandshakeFirst = true, tlsRequireClientCert = true) { server ->
			val serverCertPem = assertNotNull(server.tlsServerCertPem)
			val client =
				NatsClient {
					this.server = server.tlsUri!!
					transport = TcpTransport
					tls {
						caCertificates(serverCertPem)
						tlsFirst = true
					}
					maxReconnects = 1
				}
			try {
				val result = client.connect()
				assertTrue(result.isFailure, "expected handshake to fail without client cert, but got: ${result.getOrNull()}")
			} finally {
				try {
					client.disconnect()
				} catch (_: Throwable) {
				}
			}
		}
}

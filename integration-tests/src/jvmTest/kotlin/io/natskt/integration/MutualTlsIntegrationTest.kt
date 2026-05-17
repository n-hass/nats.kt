package io.natskt.integration

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
	fun `mTLS server rejects connection without client cert`() =
		RemoteNatsHarness.runBlocking(enableTls = true, tlsRequireClientCert = true) { server ->
			val serverCertPem = assertNotNull(server.tlsServerCertPem)
			val client =
				NatsClient {
					this.server = server.tlsUri!!
					transport = TcpTransport
					tls { caCertificates(serverCertPem) } // no clientCertificate
					maxReconnects = 1
				}
			try {
				val result = client.connect()
				assertTrue(result.isFailure, "expected handshake to fail without client cert, but got: ${result.getOrNull()}")
			} finally {
				// Ensure the client tears down before the test scope exits — when the server
				// closes mid-handshake, Ktor's TLS pipeline can produce a stray broken-pipe in
				// a sibling coroutine that the harness's exception handler would otherwise
				// flag as an unhandled failure.
				try {
					client.disconnect()
				} catch (_: Throwable) {
				}
			}
		}
}

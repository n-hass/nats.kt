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
			val result = client.connect()
			assertTrue(result.isFailure, "expected handshake to fail without client cert, but got: ${result.getOrNull()}")
		}
}

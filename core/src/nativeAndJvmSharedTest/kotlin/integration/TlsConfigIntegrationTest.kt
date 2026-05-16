package integration

import harness.RemoteNatsHarness
import harness.runBlocking
import io.natskt.NatsClient
import io.natskt.api.internal.InternalNatsApi
import io.natskt.client.transport.TcpTransport
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TlsConfigIntegrationTest {
	@OptIn(InternalNatsApi::class)
	@Test
	fun `caCertificates connects when bundle includes the server cert`() =
		RemoteNatsHarness.runBlocking(enableTls = true) { server ->
			val serverCertPem = assertNotNull(server.tlsServerCertPem, "harness must expose server cert PEM when TLS is enabled")
			val client =
				NatsClient {
					this.server = server.tlsUri!!
					transport = TcpTransport
					tls { caCertificates(serverCertPem) }
					maxReconnects = 1
				}
			val result = client.connect()
			assertTrue(result.isSuccess, "connect failed: ${result.exceptionOrNull()}")
			client.disconnect()
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `caCertificates rejects connection when bundle does not include the server cert`() =
		RemoteNatsHarness.runBlocking(enableTls = true) { server ->
			val client =
				NatsClient {
					this.server = server.tlsUri!!
					transport = TcpTransport
					tls { caCertificates(UNRELATED_CA_PEM) }
					connectTimeout = 1.seconds
					maxReconnects = 1
				}
			val result = client.connect()
			assertTrue(result.isFailure, "expected connect to fail, but got: ${result.getOrNull()}")
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `tlsFirst connects to handshake_first server`() =
		RemoteNatsHarness.runBlocking(enableTls = true, tlsHandshakeFirst = true) { server ->
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
			val result = client.connect()
			assertTrue(result.isSuccess, "tlsFirst connect failed: ${result.exceptionOrNull()}")
			client.disconnect()
		}

	@OptIn(InternalNatsApi::class)
	@Test
	fun `default ordering against handshake_first server fails`() =
		RemoteNatsHarness.runBlocking(enableTls = true, tlsHandshakeFirst = true) { server ->
			val serverCertPem = assertNotNull(server.tlsServerCertPem)
			val client =
				NatsClient {
					this.server = server.tlsUri!!
					transport = TcpTransport
					tls {
						caCertificates(serverCertPem)
						// tlsFirst not set — client will try to read INFO over the plaintext socket,
						// but the server is waiting for a TLS handshake first. The connection
						// either times out or breaks early.
					}
					connectTimeout = 1.seconds
					maxReconnects = 1
				}
			val result = client.connect()
			assertTrue(result.isFailure, "expected connect to fail when tlsFirst is required but not set")
		}
}

// Self-signed P-256 ECDSA certificate generated for these tests with:
//   openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 \
//     -keyout /dev/null -nodes -days 7300 \
//     -subj "/CN=natskt-test-unrelated-ca"
// The key is discarded — only the public certificate is embedded so the test
// can supply trust material that does NOT chain to the harness's server cert.
private val UNRELATED_CA_PEM =
	"""
	-----BEGIN CERTIFICATE-----
	MIIBmjCCAUGgAwIBAgIUQoxmIWQ+f/tEONZqd12+v5Yz4CMwCgYIKoZIzj0EAwIw
	IzEhMB8GA1UEAwwYbmF0c2t0LXRlc3QtdW5yZWxhdGVkLWNhMB4XDTI2MDUxMDEy
	MjU0NFoXDTQ2MDUwNTEyMjU0NFowIzEhMB8GA1UEAwwYbmF0c2t0LXRlc3QtdW5y
	ZWxhdGVkLWNhMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEMJXgmUrc1R/8FsF1
	QD81pzBqofhkUU2c3DFxDgPJGNQEGGuMVap4QFKlLo+SDub2SzvS+0+o3qerr+d3
	gB/QZqNTMFEwHQYDVR0OBBYEFGlKtmxP4/zyzy6XHLc1do6Hg7kTMB8GA1UdIwQY
	MBaAFGlKtmxP4/zyzy6XHLc1do6Hg7kTMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZI
	zj0EAwIDRwAwRAIgDWuFKuOaCjfSMSvOddTerKj9ld8xpxUCvIvdnRkHoroCIDdK
	c66+Jnr2pH4dvoUU/N07TL/ByMjxAanWSrVr27rB
	-----END CERTIFICATE-----
	""".trimIndent()

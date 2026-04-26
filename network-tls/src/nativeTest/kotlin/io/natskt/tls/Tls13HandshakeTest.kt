package io.natskt.tls

import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RFC 8446 §4 — TLS 1.3 handshake and cipher suite negotiation tests.
 *
 * Verifies that the client can complete a full TLS 1.3 handshake and correctly
 * negotiate each supported cipher suite. Each cipher-specific test connects to
 * a server endpoint that ONLY supports that cipher suite — success proves the
 * client negotiated it.
 */
class Tls13HandshakeTest {
	/**
	 * RFC 8446 §4: A full TLS 1.3 handshake completes successfully and the
	 * connection is usable for application data.
	 */
	@Test
	fun `completes TLS 1_3 handshake`() =
		tlsTest {
			assertTlsEcho(TlsTestPorts.tls13Only, message = "tls13-handshake")
		}

	/**
	 * RFC 8446 §4: After a successful TLS 1.3 handshake, application data
	 * can be exchanged bidirectionally.
	 */
	@Test
	fun `exchanges data over TLS 1_3`() =
		tlsTest {
			val tls = connectTls(TlsTestPorts.tls13Only)
			try {
				for (i in 1..5) {
					val msg = "message-$i"
					val data = msg.encodeToByteArray()
					tls.output.writeFully(data)
					tls.output.flush()

					val echo = ByteArray(data.size)
					tls.input.readFully(echo)
					assertEquals(msg, echo.decodeToString())
				}
			} finally {
				tls.close()
			}
		}

	/**
	 * RFC 8446 §9.2: TLS_AES_128_GCM_SHA256 (0x1301) is mandatory-to-implement.
	 * Server only offers this suite — handshake success proves the client supports it.
	 */
	@Test
	fun `negotiates TLS_AES_128_GCM_SHA256`() =
		tlsTest {
			assertTlsEcho(TlsTestPorts.tls13Aes128, message = "aes128gcm")
		}

	/**
	 * RFC 8446 §B.4: TLS_AES_256_GCM_SHA384 (0x1302).
	 * Server only offers this suite — handshake success proves the client supports it.
	 */
	@Test
	fun `negotiates TLS_AES_256_GCM_SHA384`() =
		tlsTest {
			assertTlsEcho(TlsTestPorts.tls13Aes256, message = "aes256gcm")
		}

	/**
	 * RFC 8446 §B.4: TLS_CHACHA20_POLY1305_SHA256 (0x1303).
	 * Server only offers this suite — handshake success proves the client supports it.
	 */
	@Test
	fun `negotiates TLS_CHACHA20_POLY1305_SHA256`() =
		tlsTest {
			assertTlsEcho(TlsTestPorts.tls13Chacha, message = "chacha20")
		}

	/**
	 * RFC 8446 §4.1.4: The server MAY send a HelloRetryRequest if the client's
	 * initial key_share does not include an acceptable group. The client MUST then
	 * send a new ClientHello with an updated key_share for the requested group.
	 *
	 * The server only accepts P-384, but the client initially offers P-256.
	 * The JDK server sends an HRR requesting P-384. Success proves the client
	 * handles HRR correctly.
	 */
	@Test
	fun `handles HelloRetryRequest when server requires different group`() =
		tlsTest {
			assertTlsEcho(TlsTestPorts.tls13HrrP384, message = "hrr-p384")
		}
}

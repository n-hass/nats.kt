package io.natskt.tls

import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RFC 5246 — TLS 1.2 handshake tests.
 *
 * Connects to a TLS 1.2-only server endpoint (JDK SSLServerSocket configured for TLSv1.2).
 * Success proves the client correctly implements the TLS 1.2 handshake:
 * ClientHello → ServerHello, Certificate, ServerKeyExchange, ServerHelloDone →
 * ClientKeyExchange, ChangeCipherSpec, Finished → ChangeCipherSpec, Finished.
 */
class Tls12HandshakeTest {
	/**
	 * RFC 5246 §7.3: A full TLS 1.2 handshake completes and the connection is usable.
	 */
	@Test
	fun `completes TLS 1_2 handshake`() =
		tlsTest {
			assertTlsEcho(TlsTestPorts.tls12Only, message = "tls12-handshake")
		}

	/**
	 * RFC 5246 §6.2.3: After a successful TLS 1.2 handshake, application data
	 * can be exchanged over the encrypted record layer.
	 */
	@Test
	fun `exchanges data over TLS 1_2`() =
		tlsTest {
			val tls = connectTls(TlsTestPorts.tls12Only)
			try {
				for (i in 1..5) {
					val msg = "tls12-msg-$i"
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
	 * TLS 1.2 with ECDHE_ECDSA key exchange. The server uses an EC certificate
	 * and only offers TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256.
	 * Success proves the client supports ECDSA server authentication in TLS 1.2.
	 */
	@Test
	fun `completes TLS 1_2 ECDHE_ECDSA handshake`() =
		tlsTest {
			assertTlsEcho(TlsTestPorts.tls12EcdheEcdsa, message = "ecdhe-ecdsa")
		}

	/**
	 * TLS 1.2 with ECDHE_RSA key exchange. The server uses an RSA certificate
	 * and only offers TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256.
	 * Success proves the client supports RSA server authentication in TLS 1.2.
	 */
	@Test
	fun `completes TLS 1_2 ECDHE_RSA handshake`() =
		tlsTest {
			assertTlsEcho(TlsTestPorts.tls12EcdheRsa, message = "ecdhe-rsa")
		}
}

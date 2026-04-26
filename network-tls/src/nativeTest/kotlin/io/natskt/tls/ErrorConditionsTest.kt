package io.natskt.tls

import io.natskt.tls.internal.TlsException
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * RFC 8446 §5, §6 — Error condition and malformed data tests.
 *
 * Verifies that the TLS client correctly rejects malformed or invalid data
 * from the server by raising TlsException. These endpoints send deliberately
 * broken data to exercise error paths.
 */
class ErrorConditionsTest {
	/**
	 * When the server sends random non-TLS bytes, the client should detect that
	 * the data is not a valid TLS record and raise a TlsException.
	 */
	@Test
	fun `raises TlsException on garbage data`() =
		tlsTest {
			assertFailsWith<TlsException> {
				connectTls(TlsTestPorts.garbage)
			}
		}

	/**
	 * RFC 8446 §5.1: A TLS record has a 5-byte header. If the server sends only
	 * a partial header (3 bytes) then closes the connection, the client should
	 * detect the truncation and raise a TlsException.
	 */
	@Test
	fun `raises TlsException on truncated TLS record`() =
		tlsTest {
			assertFailsWith<TlsException> {
				connectTls(TlsTestPorts.truncatedRecord)
			}
		}

	/**
	 * RFC 8446 §5.1: Implementations MUST NOT send record layer fragments larger than
	 * 2^14+256 bytes. A client receiving an oversized record length field should reject it.
	 */
	@Test
	fun `raises TlsException on oversized record`() =
		tlsTest {
			assertFailsWith<TlsException> {
				connectTls(TlsTestPorts.oversizedRecord)
			}
		}
}

package io.natskt.tls

import io.natskt.tls.internal.TlsException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * RFC 8446 §6 — Alert protocol tests.
 *
 * Verifies that the TLS client correctly handles alert messages from the server,
 * including fatal alerts that terminate the connection and unexpected connection closures.
 */
class AlertProtocolTest {
	/**
	 * RFC 8446 §6.2: Upon receiving a fatal alert, the receiver MUST immediately
	 * close the connection. The client should raise a TlsException.
	 *
	 * The fatal_alert endpoint sends a TLS fatal handshake_failure alert record
	 * immediately after accepting the TCP connection.
	 */
	@Test
	fun `raises TlsException on fatal alert during handshake`() =
		tlsTest {
			assertFailsWith<TlsException> {
				connectTls(TlsTestPorts.fatalAlert)
			}
		}

	/**
	 * When the server immediately closes the TCP connection before any TLS data
	 * is exchanged, this is a transport-level event (not a TLS error). The client
	 * should propagate the underlying I/O exception (e.g. EOFException) rather
	 * than hanging indefinitely.
	 */
	@Test
	fun `raises exception on immediate server close during handshake`() =
		tlsTest {
			val error =
				assertFailsWith<Exception> {
					connectTls(TlsTestPorts.immediateClose)
				}
			assertNotNull(error.message, "Exception should have a message describing the transport failure")
		}
}

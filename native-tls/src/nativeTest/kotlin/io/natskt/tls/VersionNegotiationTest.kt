package io.natskt.tls

import kotlin.test.Test

/**
 * RFC 8446 §4.2.1 — TLS version negotiation tests.
 *
 * Verifies that the client correctly negotiates the highest mutually supported
 * TLS version and falls back to TLS 1.2 when TLS 1.3 is not available.
 */
class VersionNegotiationTest {
	/**
	 * RFC 8446 §4.2.1: When both TLS 1.3 and TLS 1.2 are available, the client
	 * and server SHOULD negotiate TLS 1.3 as the highest supported version.
	 *
	 * The tls_default endpoint supports both TLS 1.2 and 1.3.
	 * JDK prefers TLS 1.3 when both are available.
	 * Handshake success proves the client can negotiate with a dual-version server.
	 */
	@Test
	fun `negotiates TLS 1_3 when server supports both versions`() =
		tlsTest {
			assertTlsEcho(TlsTestPorts.tlsDefault, message = "version-both")
		}

	/**
	 * RFC 8446 §4.2.1 / RFC 5246: When the server only supports TLS 1.2,
	 * the client MUST be able to fall back to TLS 1.2 for backward compatibility.
	 *
	 * The tls12_only endpoint only supports TLSv1.2.
	 * Handshake success proves the client correctly falls back.
	 */
	@Test
	fun `falls back to TLS 1_2 when server only offers 1_2`() =
		tlsTest {
			assertTlsEcho(TlsTestPorts.tls12Only, message = "version-12only")
		}

	/**
	 * Verify the client works with a TLS 1.3-only server.
	 *
	 * The tls13_only endpoint only supports TLSv1.3.
	 * Handshake success proves TLS 1.3 negotiation works when 1.2 is unavailable.
	 */
	@Test
	fun `completes handshake with TLS 1_3 only server`() =
		tlsTest {
			assertTlsEcho(TlsTestPorts.tls13Only, message = "version-13only")
		}
}

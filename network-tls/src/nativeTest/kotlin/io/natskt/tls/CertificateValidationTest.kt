package io.natskt.tls

import io.natskt.tls.internal.TlsException
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * RFC 5280 — Certificate validation behavior tests.
 *
 * The TLS test server uses a self-signed certificate that is NOT in the system
 * trust store. This allows testing that:
 * - verifyCertificates=true correctly rejects untrusted CAs
 * - verifyCertificates=false bypasses validation
 *
 * Note: Specific validation scenarios (expired certs, hostname mismatch) are tested
 * in the existing linuxTest/CertValidatorLinuxTest.kt which tests the platform-specific
 * validateCertificateChain() function directly.
 */
class CertificateValidationTest {
	/**
	 * RFC 5280 §6: When certificate validation is enabled, the client MUST reject
	 * certificates from untrusted CAs. The test server uses a self-signed cert
	 * not in the system trust store, so the handshake should fail.
	 */
	@Test
	fun `rejects untrusted CA when verifyCertificates is true`() =
		tlsTest {
			assertFailsWith<TlsException> {
				connectTls(
					port = TlsTestPorts.tlsDefault,
					serverName = "localhost",
					verifyCertificates = true,
				)
			}
		}

	/**
	 * When verifyCertificates is false, the client MUST skip certificate chain
	 * validation and allow the connection to proceed regardless of the CA trust.
	 */
	@Test
	fun `accepts untrusted CA when verifyCertificates is false`() =
		tlsTest {
			assertTlsEcho(
				port = TlsTestPorts.tlsDefault,
				message = "no-verify",
				serverName = "localhost",
			)
		}
}

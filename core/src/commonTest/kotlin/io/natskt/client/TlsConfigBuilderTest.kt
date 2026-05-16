package io.natskt.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TlsConfigBuilderTest {
	private val sampleCaPem =
		"""
		-----BEGIN CERTIFICATE-----
		MIIBQDCB6KADAgECAhRVKv2P6/Y6Z3vH8XSWJklr5lpRqzAFBgMrZXAwFDESMBAG
		A1UEAwwJVGVzdCBSb290MB4XDTI1MDEwMTAwMDAwMFoXDTM1MDEwMTAwMDAwMFow
		FDESMBAGA1UEAwwJVGVzdCBSb290MCowBQYDK2VwAyEAYWFhYWFhYWFhYWFhYWFh
		YWFhYWFhYWFhYWFhYWFhYWGjUzBRMB0GA1UdDgQWBBQAAAAAAAAAAAAAAAAAAAAA
		AAAAADAfBgNVHSMEGDAWgBQAAAAAAAAAAAAAAAAAAAAAAAAAADAPBgNVHRMBAf8E
		BTADAQH/MAUGAytlcANBAEHEC1qYqJ09XmJTKt4fK9rmUqtH7c1RXKsxX76vTlPH
		1+1z0LmNHEWiybYJ3oF9+vULRGLFBhDcKGWhuKrXrAA=
		-----END CERTIFICATE-----
		""".trimIndent()

	@Test
	fun `default config has no trust material and no client cert`() {
		val cfg = TlsConfigBuilder().build()

		assertFalse(cfg.acceptAnyServerCertificate)
		assertFalse(cfg.tlsFirst)
		assertFalse(cfg.hasCustomTrust)
		assertFalse(cfg.hasClientCertificate)
		assertEquals(emptyList(), cfg.caCertificatesDer)
	}

	@Test
	fun `caCertificates parses single PEM block`() {
		val cfg =
			TlsConfigBuilder()
				.apply { caCertificates(sampleCaPem) }
				.build()

		assertTrue(cfg.hasCustomTrust)
		assertEquals(1, cfg.caCertificatesDer.size)
		assertTrue(cfg.caCertificatesDer.first().isNotEmpty())
	}

	@Test
	fun `caCertificates accumulates across multiple calls`() {
		val cfg =
			TlsConfigBuilder()
				.apply {
					caCertificates(sampleCaPem)
					caCertificates(sampleCaPem)
				}.build()

		assertEquals(2, cfg.caCertificatesDer.size)
	}

	@Test
	fun `caCertificates rejects PEM with no certificate blocks`() {
		val noCerts = "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----"

		assertFailsWith<IllegalArgumentException> {
			TlsConfigBuilder().caCertificates(noCerts)
		}
	}

	@Test
	fun `tlsFirst defaults false and is independently settable`() {
		val cfg =
			TlsConfigBuilder()
				.apply { tlsFirst = true }
				.build()

		assertTrue(cfg.tlsFirst)
		assertFalse(cfg.acceptAnyServerCertificate)
	}

	@Test
	fun `parsePemBundle preserves block order`() {
		val bundle =
			sampleCaPem +
				"\n" +
				sampleCaPem.replace("Test Root", "Other Root")

		val blocks = parsePemBundle(bundle)

		assertEquals(2, blocks.size)
		blocks.forEach { assertEquals("CERTIFICATE", it.label) }
	}

	@Test
	fun `parsePemBundle returns empty list when no markers present`() {
		assertEquals(0, parsePemBundle("not pem at all").size)
	}

	@Test
	fun `tls block on builder accumulates across calls`() {
		val config =
			ClientConfigurationBuilder()
				.apply {
					server = "tls://example.com:4222"
					tls { acceptAnyServerCertificate = true }
					tls { tlsFirst = true }
				}.build()

		assertTrue(config.tlsConfig.acceptAnyServerCertificate)
		assertTrue(config.tlsConfig.tlsFirst)
	}
}

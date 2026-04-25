@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.natskt.tls.cert

import io.natskt.tls.internal.TlsException
import io.natskt.tls.openssl.X509_STORE_add_cert
import io.natskt.tls.openssl.X509_STORE_free
import io.natskt.tls.openssl.X509_STORE_new
import io.natskt.tls.openssl.X509_free
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CertValidatorLinuxTest {
	// --- Trust store ---

	@Test
	fun validChainWithCustomCaPasses() {
		val gen = TestCertGenerator()
		val ca = gen.generateCa("Test CA")
		val leaf = gen.generateLeaf(ca, cn = "localhost", sans = "DNS:localhost")

		val store = X509_STORE_new()!!
		val caCert = parseDerCert(ca.der)
		try {
			X509_STORE_add_cert(store, caCert)
			validateWithStore(listOf(leaf), hostname = "localhost", store = store)
		} finally {
			X509_free(caCert)
			X509_STORE_free(store)
		}
	}

	@Test
	fun untrustedCaFails() {
		val gen = TestCertGenerator()
		val ca = gen.generateCa("Untrusted CA")
		val leaf = gen.generateLeaf(ca, cn = "localhost", sans = "DNS:localhost")

		val store = X509_STORE_new()!!
		// Don't add CA — store is empty
		try {
			assertFailsWith<TlsException> {
				validateWithStore(listOf(leaf), hostname = null, store = store)
			}
		} finally {
			X509_STORE_free(store)
		}
	}

	@Test
	fun intermediateChainValidates() {
		val gen = TestCertGenerator()
		val root = gen.generateCa("Root CA")
		val intermediate = gen.generateIntermediateCa(root, "Intermediate CA")
		val leaf = gen.generateLeaf(intermediate, cn = "localhost", sans = "DNS:localhost")

		val store = X509_STORE_new()!!
		val rootCert = parseDerCert(root.der)
		try {
			X509_STORE_add_cert(store, rootCert)
			// Chain: [leaf, intermediate]
			validateWithStore(listOf(leaf, intermediate.der), hostname = "localhost", store = store)
		} finally {
			X509_free(rootCert)
			X509_STORE_free(store)
		}
	}

	@Test
	fun missingIntermediateFails() {
		val gen = TestCertGenerator()
		val root = gen.generateCa("Root CA")
		val intermediate = gen.generateIntermediateCa(root, "Intermediate CA")
		val leaf = gen.generateLeaf(intermediate, cn = "localhost", sans = "DNS:localhost")

		val store = X509_STORE_new()!!
		val rootCert = parseDerCert(root.der)
		try {
			X509_STORE_add_cert(store, rootCert)
			// Chain: [leaf] — intermediate missing
			assertFailsWith<TlsException> {
				validateWithStore(listOf(leaf), hostname = null, store = store)
			}
		} finally {
			X509_free(rootCert)
			X509_STORE_free(store)
		}
	}

	// --- Hostname ---

	@Test
	fun matchingDnsSanPasses() {
		val gen = TestCertGenerator()
		val ca = gen.generateCa("Test CA")
		val leaf = gen.generateLeaf(ca, cn = "localhost", sans = "DNS:localhost,DNS:example.com")

		withCaStore(ca) { store ->
			validateWithStore(listOf(leaf), hostname = "localhost", store = store)
			validateWithStore(listOf(leaf), hostname = "example.com", store = store)
		}
	}

	@Test
	fun matchingIpSanPasses() {
		val gen = TestCertGenerator()
		val ca = gen.generateCa("Test CA")
		val leaf = gen.generateLeaf(ca, cn = "localhost", sans = "IP:127.0.0.1")

		withCaStore(ca) { store ->
			validateWithStore(listOf(leaf), hostname = "127.0.0.1", store = store)
		}
	}

	@Test
	fun hostnameMismatchFails() {
		val gen = TestCertGenerator()
		val ca = gen.generateCa("Test CA")
		val leaf = gen.generateLeaf(ca, cn = "localhost", sans = "DNS:localhost")

		withCaStore(ca) { store ->
			assertFailsWith<TlsException> {
				validateWithStore(listOf(leaf), hostname = "evil.example.com", store = store)
			}
		}
	}

	@Test
	fun nullHostnameSkipsHostCheck() {
		val gen = TestCertGenerator()
		val ca = gen.generateCa("Test CA")
		val leaf = gen.generateLeaf(ca, cn = "localhost", sans = "DNS:localhost")

		withCaStore(ca) { store ->
			// hostname = null means chain-only validation
			validateWithStore(listOf(leaf), hostname = null, store = store)
		}
	}

	// --- Expiry ---

	@Test
	fun expiredCertFails() {
		val gen = TestCertGenerator()
		val ca = gen.generateCa("Test CA")
		val leaf = gen.generateExpiredLeaf(ca, cn = "localhost", sans = "DNS:localhost")

		withCaStore(ca) { store ->
			assertFailsWith<TlsException> {
				validateWithStore(listOf(leaf), hostname = null, store = store)
			}
		}
	}

	@Test
	fun validCertPasses() {
		val gen = TestCertGenerator()
		val ca = gen.generateCa("Test CA")
		val leaf = gen.generateLeaf(ca, cn = "localhost", sans = "DNS:localhost")

		withCaStore(ca) { store ->
			validateWithStore(listOf(leaf), hostname = null, store = store)
		}
	}

	// --- Helpers ---

	private inline fun withCaStore(
		ca: CaResult,
		block: (kotlinx.cinterop.CPointer<io.natskt.tls.openssl.X509_STORE>) -> Unit,
	) {
		val store = X509_STORE_new()!!
		val caCert = parseDerCert(ca.der)
		try {
			X509_STORE_add_cert(store, caCert)
			block(store)
		} finally {
			X509_free(caCert)
			X509_STORE_free(store)
		}
	}
}

package io.natskt.harness.tls

import java.io.File
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Generates self-signed certificates for TLS test endpoints using JDK's `keytool`.
 * This avoids internal JDK API usage (sun.security.x509) which is inaccessible in modular JDKs.
 */
object CertificateGenerator {
	private const val KEYSTORE_PASSWORD = "changeit"
	private const val KEY_ALIAS = "tls-test"

	data class CertBundle(
		val keyStore: KeyStore,
		val sslContext: SSLContext,
		val keystoreFile: File,
	)

	fun generateEc(
		cn: String = "localhost",
		sans: List<String> = listOf("localhost", "127.0.0.1"),
	): CertBundle =
		generate(
			cn = cn,
			sans = sans,
			keyAlg = "EC",
			keySpec = listOf("-groupname", "secp256r1"),
			sigAlg = "SHA256withECDSA",
		)

	fun generateRsa(
		cn: String = "localhost",
		sans: List<String> = listOf("localhost", "127.0.0.1"),
	): CertBundle =
		generate(
			cn = cn,
			sans = sans,
			keyAlg = "RSA",
			keySpec = listOf("-keysize", "2048"),
			sigAlg = "SHA256withRSA",
		)

	private fun generate(
		cn: String,
		sans: List<String>,
		keyAlg: String,
		keySpec: List<String>,
		sigAlg: String,
	): CertBundle {
		val keystoreFile = File.createTempFile("tls-test-keystore-", ".p12")
		keystoreFile.delete() // keytool needs a non-existent path to create a new keystore
		keystoreFile.deleteOnExit()

		val sanParts =
			sans.map { san ->
				if (san.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) "ip:$san" else "dns:$san"
			}
		val sanExt = "SAN=${sanParts.joinToString(",")}"

		val javaHome = System.getProperty("java.home")
		val keytool = File(javaHome, "bin/keytool").let { if (it.exists()) it.absolutePath else "keytool" }

		val command =
			mutableListOf(
				keytool,
				"-genkeypair",
				"-alias",
				KEY_ALIAS,
				"-keyalg",
				keyAlg,
			) + keySpec +
				listOf(
					"-sigalg",
					sigAlg,
					"-keystore",
					keystoreFile.absolutePath,
					"-storetype",
					"PKCS12",
					"-storepass",
					KEYSTORE_PASSWORD,
					"-keypass",
					KEYSTORE_PASSWORD,
					"-dname",
					"CN=$cn",
					"-ext",
					sanExt,
					"-ext",
					"BasicConstraints:critical=ca:false",
					"-validity",
					"365",
				)

		val process = ProcessBuilder(command).redirectErrorStream(true).start()
		val output = process.inputStream.bufferedReader().readText()
		val exitCode = process.waitFor()
		if (exitCode != 0) {
			error("keytool failed (exit $exitCode): $output")
		}

		val keyStore = KeyStore.getInstance("PKCS12")
		keystoreFile.inputStream().use { keyStore.load(it, KEYSTORE_PASSWORD.toCharArray()) }

		val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
		kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray())

		val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
		tmf.init(keyStore)

		val sslContext = SSLContext.getInstance("TLS")
		sslContext.init(kmf.keyManagers, tmf.trustManagers, null)

		println("Certificate generated ($keyAlg): CN=$cn, SANs=$sans")

		return CertBundle(keyStore, sslContext, keystoreFile)
	}
}

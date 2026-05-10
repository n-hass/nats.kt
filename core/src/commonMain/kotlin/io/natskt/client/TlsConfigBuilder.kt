package io.natskt.client

@DslMarker
public annotation class TlsConfigDsl

/**
 * DSL builder for the [ClientConfigurationBuilder.tls] block.
 *
 * Materials are accepted in PEM form (string or byte array) for cross-platform portability and
 * stored as DER under the hood. See [TlsConfig] for the resolved shape.
 */
@TlsConfigDsl
public class TlsConfigBuilder internal constructor() {
	/**
	 * When `true`, the client accepts any certificate the server presents — including
	 * self-signed and expired certificates — without verifying it against trust roots
	 * or hostname.
	 *
	 * Intended for local development and testing only. Setting this in production silently
	 * defeats the integrity guarantees of TLS.
	 */
	public var acceptAnyServerCertificate: Boolean = false

	/**
	 * When `true`, the client performs the TLS handshake immediately after the TCP connection
	 * is established — before reading the server's `INFO`. Required for NATS servers configured
	 * with `tls.handshake_first: true` (and for some managed-NATS deployments where the load
	 * balancer terminates TLS only on TLS-first connections).
	 *
	 * Independent of the URL scheme: `tls://` says "this URL is for the TLS port"; `tlsFirst`
	 * says "skip the plaintext `INFO` exchange entirely".
	 *
	 * Has no effect on WebSocket transports — the `wss://` upgrade already terminates TLS
	 * before any NATS protocol bytes are exchanged.
	 */
	public var tlsFirst: Boolean = false

	private val caCerts = mutableListOf<ByteArray>()
	private val clientChain = mutableListOf<ByteArray>()
	private var clientKey: ByteArray? = null
	private var clientKeyAlgorithm: TlsPrivateKeyAlgorithm? = null

	/**
	 * Add one or more PEM-encoded CA certificates to the trust roots. May be called multiple
	 * times to accumulate trust material.
	 *
	 * Once any CA is supplied, the platform default trust store is *not* consulted — supply
	 * every root the client should trust. Pass an empty bundle (or omit this call entirely)
	 * to use the platform default.
	 */
	public fun caCertificates(pem: String) {
		val blocks = parsePemBundle(pem)
		val certs = blocks.filter { it.label.endsWith("CERTIFICATE") }
		require(certs.isNotEmpty()) { "No CERTIFICATE blocks found in PEM bundle" }
		certs.forEach { caCerts += it.der }
	}

	public fun caCertificates(pem: ByteArray) {
		caCertificates(pem.decodeToString())
	}

	/**
	 * Provide a client certificate (and chain) plus its private key for mutual TLS.
	 *
	 * The cert PEM may contain multiple `CERTIFICATE` blocks — the leaf must come first,
	 * followed by any intermediates. The key PEM must contain a single PKCS#8 `PRIVATE KEY`
	 * block. Convert legacy `RSA PRIVATE KEY` / `EC PRIVATE KEY` PEMs with
	 * `openssl pkcs8 -topk8 -nocrypt -in legacy.pem -out pkcs8.pem` first.
	 */
	public fun clientCertificate(
		certPem: String,
		keyPem: String,
	) {
		val certBlocks = parsePemBundle(certPem).filter { it.label.endsWith("CERTIFICATE") }
		require(certBlocks.isNotEmpty()) { "No CERTIFICATE blocks found in client cert PEM" }

		val keyBlock =
			parsePemBundle(keyPem).firstOrNull { it.label == "PRIVATE KEY" }
				?: error(
					"No PKCS#8 PRIVATE KEY block found in client key PEM. " +
						"Convert legacy RSA/EC private keys with: openssl pkcs8 -topk8 -nocrypt -in legacy.pem -out pkcs8.pem",
				)

		clientChain.clear()
		certBlocks.forEach { clientChain += it.der }
		clientKey = keyBlock.der
		clientKeyAlgorithm = detectPkcs8Algorithm(keyBlock.der)
	}

	public fun clientCertificate(
		certPem: ByteArray,
		keyPem: ByteArray,
	) {
		clientCertificate(certPem.decodeToString(), keyPem.decodeToString())
	}

	internal fun build(): TlsConfig =
		TlsConfig(
			acceptAnyServerCertificate = acceptAnyServerCertificate,
			caCertificatesDer = caCerts.toList(),
			clientCertificateChainDer = clientChain.toList(),
			clientPrivateKeyDer = clientKey,
			clientPrivateKeyAlgorithm = clientKeyAlgorithm,
			tlsFirst = tlsFirst,
		)
}

/**
 * Detect the algorithm of a PKCS#8 PrivateKeyInfo by inspecting its embedded AlgorithmIdentifier.
 *
 * Looks for the well-known OIDs: 1.2.840.113549.1.1.1 (rsaEncryption) and 1.2.840.10045.2.1 (ecPublicKey).
 */
private fun detectPkcs8Algorithm(der: ByteArray): TlsPrivateKeyAlgorithm {
	val rsaOid = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01)
	val ecOid = byteArrayOf(0x06, 0x07, 0x2A, 0x86.toByte(), 0x48, 0xCE.toByte(), 0x3D, 0x02, 0x01)
	if (der.containsSubsequence(rsaOid)) return TlsPrivateKeyAlgorithm.Rsa
	if (der.containsSubsequence(ecOid)) return TlsPrivateKeyAlgorithm.Ec
	error("Could not determine algorithm of PKCS#8 PRIVATE KEY block (expected RSA or EC)")
}

private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean {
	if (needle.isEmpty() || needle.size > size) return false
	outer@ for (i in 0..size - needle.size) {
		for (j in needle.indices) {
			if (this[i + j] != needle[j]) continue@outer
		}
		return true
	}
	return false
}

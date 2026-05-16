package io.natskt.tls.cert

/**
 * Validates a certificate chain against the platform trust store, or against the supplied
 * [trustAnchorsDer] when non-empty.
 *
 * @param certs DER-encoded X.509 certificates, leaf first
 * @param hostname server hostname for SNI verification (null to skip)
 * @param trustAnchorsDer optional DER-encoded CA certificates that replace the platform default
 *   trust store. Empty means "use platform default."
 * @throws TlsException if validation fails
 */
internal expect fun validateCertificateChain(
	certs: List<ByteArray>,
	hostname: String?,
	trustAnchorsDer: List<ByteArray>,
)

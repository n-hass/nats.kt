package io.natskt.tls.cert

/**
 * Validates a certificate chain against the platform trust store.
 *
 * @param certs DER-encoded X.509 certificates, leaf first
 * @param hostname server hostname for SNI verification (null to skip)
 * @throws TlsException if validation fails
 */
internal expect fun validateCertificateChain(
	certs: List<ByteArray>,
	hostname: String?,
)

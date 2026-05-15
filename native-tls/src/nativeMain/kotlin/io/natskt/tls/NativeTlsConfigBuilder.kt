package io.natskt.tls

public class NativeTlsConfigBuilder {
	public var serverName: String? = null
	public var verifyCertificates: Boolean = true

	/**
	 * DER-encoded X.509 certificates to use as trust anchors instead of the platform default
	 * trust store. When the list is empty, the platform default store is consulted.
	 *
	 * Ignored when [verifyCertificates] is `false`.
	 */
	public var trustAnchorsDer: List<ByteArray> = emptyList()
}

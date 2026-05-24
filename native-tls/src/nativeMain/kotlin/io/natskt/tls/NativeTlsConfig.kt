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

	internal fun build(): NativeTlsConfig {
		val serverName = this.serverName ?: throw IllegalArgumentException("server name must be specified")
		return NativeTlsConfig(
			serverName = serverName,
			verifyCertificates = this.verifyCertificates,
			trustAnchorsDer = this.trustAnchorsDer,
		)
	}
}

@ConsistentCopyVisibility
public data class NativeTlsConfig internal constructor(
	public val serverName: String,
	public val verifyCertificates: Boolean,
	public val trustAnchorsDer: List<ByteArray>,
)

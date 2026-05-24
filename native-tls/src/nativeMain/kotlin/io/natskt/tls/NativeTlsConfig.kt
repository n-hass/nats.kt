package io.natskt.tls

import io.natskt.client.TlsPrivateKeyAlgorithm

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

	/**
	 * DER-encoded X.509 client certificate chain to present during the TLS handshake (mTLS).
	 * Leaf certificate first, followed by any intermediates. Empty disables client authentication.
	 *
	 * When set, [clientPrivateKeyDer] and [clientPrivateKeyAlgorithm] must also be set.
	 */
	public var clientCertificateChainDer: List<ByteArray> = emptyList()

	/** PKCS#8 DER-encoded private key matching [clientCertificateChainDer]'s leaf. */
	public var clientPrivateKeyDer: ByteArray? = null

	/** Algorithm of [clientPrivateKeyDer]. Required when a client key is set. */
	public var clientPrivateKeyAlgorithm: TlsPrivateKeyAlgorithm? = null

	internal fun build(): NativeTlsConfig {
		val serverName = this.serverName ?: throw IllegalArgumentException("server name must be specified")
		val chain = this.clientCertificateChainDer
		val key = this.clientPrivateKeyDer
		val algorithm = this.clientPrivateKeyAlgorithm
		if (chain.isNotEmpty() || key != null || algorithm != null) {
			require(chain.isNotEmpty()) { "clientCertificateChainDer must be non-empty when a client identity is configured" }
			require(chain.all { it.isNotEmpty() }) { "clientCertificateChainDer entries must be non-empty" }
			require(key != null && key.isNotEmpty()) { "clientPrivateKeyDer must be set and non-empty when a client identity is configured" }
			require(algorithm != null) { "clientPrivateKeyAlgorithm must be set when a client identity is configured" }
		}
		return NativeTlsConfig(
			serverName = serverName,
			verifyCertificates = this.verifyCertificates,
			trustAnchorsDer = this.trustAnchorsDer,
			clientCertificateChainDer = chain,
			clientPrivateKeyDer = key,
			clientPrivateKeyAlgorithm = algorithm,
		)
	}
}

@ConsistentCopyVisibility
public data class NativeTlsConfig internal constructor(
	public val serverName: String,
	public val verifyCertificates: Boolean,
	public val trustAnchorsDer: List<ByteArray>,
	public val clientCertificateChainDer: List<ByteArray>,
	public val clientPrivateKeyDer: ByteArray?,
	public val clientPrivateKeyAlgorithm: TlsPrivateKeyAlgorithm?,
) {
	public val hasClientCertificate: Boolean
		get() = clientCertificateChainDer.isNotEmpty() && clientPrivateKeyDer != null && clientPrivateKeyAlgorithm != null

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as NativeTlsConfig

		if (verifyCertificates != other.verifyCertificates) return false
		if (serverName != other.serverName) return false
		if (trustAnchorsDer != other.trustAnchorsDer) return false
		if (clientCertificateChainDer != other.clientCertificateChainDer) return false
		if (!clientPrivateKeyDer.contentEquals(other.clientPrivateKeyDer)) return false
		if (clientPrivateKeyAlgorithm != other.clientPrivateKeyAlgorithm) return false
		if (hasClientCertificate != other.hasClientCertificate) return false

		return true
	}

	override fun hashCode(): Int {
		var result = verifyCertificates.hashCode()
		result = 31 * result + serverName.hashCode()
		result = 31 * result + trustAnchorsDer.hashCode()
		result = 31 * result + clientCertificateChainDer.hashCode()
		result = 31 * result + (clientPrivateKeyDer?.contentHashCode() ?: 0)
		result = 31 * result + (clientPrivateKeyAlgorithm?.hashCode() ?: 0)
		result = 31 * result + hasClientCertificate.hashCode()
		return result
	}
}

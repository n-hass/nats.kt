package io.natskt.client

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Resolved TLS configuration consumed by the transport layer.
 *
 * Build via the `tls { ... }` DSL on [ClientConfigurationBuilder]; transports apply this
 * to the underlying engine (Ktor TLSConfigBuilder on JVM, the Kotlin/Native TLS handshake on
 * Native, or the platform Ktor engine for WebSocket transports).
 *
 * Materials are stored as DER-encoded bytes for cross-platform portability.
 */
public class TlsConfig(
	public val acceptAnyServerCertificate: Boolean,
	public val caCertificatesDer: List<ByteArray>,
	public val clientCertificateChainDer: List<ByteArray>,
	public val clientPrivateKeyDer: ByteArray?,
	public val clientPrivateKeyAlgorithm: TlsPrivateKeyAlgorithm?,
	public val tlsFirst: Boolean,
) {
	public val hasCustomTrust: Boolean get() = caCertificatesDer.isNotEmpty()
	public val hasClientCertificate: Boolean get() = clientCertificateChainDer.isNotEmpty() && clientPrivateKeyDer != null

	public companion object {
		public val Default: TlsConfig =
			TlsConfig(
				acceptAnyServerCertificate = false,
				caCertificatesDer = emptyList(),
				clientCertificateChainDer = emptyList(),
				clientPrivateKeyDer = null,
				clientPrivateKeyAlgorithm = null,
				tlsFirst = false,
			)
	}
}

/**
 * Algorithm class for a [TlsConfig.clientPrivateKeyDer] payload.
 *
 * Drives JVM `KeyFactory` selection and may inform Native signature wiring once mTLS is wired
 * into the Kotlin/Native handshake.
 */
public enum class TlsPrivateKeyAlgorithm {
	Rsa,
	Ec,
}

/**
 * Parses a PEM-encoded bundle into a list of DER blobs, in the order they appear.
 *
 * Recognises any block whose header starts with `BEGIN` and ends with `CERTIFICATE`,
 * `PRIVATE KEY`, `RSA PRIVATE KEY`, or `EC PRIVATE KEY`. Unknown blocks are skipped.
 */
public fun parsePemBundle(pem: String): List<PemBlock> = PemReader(pem).readAll()

@OptIn(ExperimentalEncodingApi::class)
internal fun decodePemBase64(content: String): ByteArray = Base64.decode(content.replace("\\s+".toRegex(), ""))

public class PemBlock(
	public val label: String,
	public val der: ByteArray,
)

private class PemReader(
	private val text: String,
) {
	private var pos = 0

	fun readAll(): List<PemBlock> {
		val blocks = mutableListOf<PemBlock>()
		while (true) {
			val block = readNext() ?: break
			blocks += block
		}
		return blocks
	}

	private fun readNext(): PemBlock? {
		while (pos < text.length) {
			val begin = text.indexOf("-----BEGIN ", pos)
			if (begin < 0) return null
			val labelEnd = text.indexOf("-----", begin + 11)
			if (labelEnd < 0) return null
			val label = text.substring(begin + 11, labelEnd).trim()
			val bodyStart = labelEnd + 5
			val end = text.indexOf("-----END ", bodyStart)
			if (end < 0) return null
			val endLabelEnd = text.indexOf("-----", end + 9)
			if (endLabelEnd < 0) return null
			val body = text.substring(bodyStart, end)
			pos = endLabelEnd + 5
			return PemBlock(label, decodePemBase64(body))
		}
		return null
	}
}

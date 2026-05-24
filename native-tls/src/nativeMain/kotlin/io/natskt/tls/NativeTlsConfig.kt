package io.natskt.tls

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

	public var soKeepAliveConfig: SoKeepAliveConfig? = null

	public fun soKeepAliveConfig(configure: SoKeepAliveConfigBuilder.() -> Unit) {
		soKeepAliveConfig = SoKeepAliveConfigBuilder().apply(configure).build()
	}

	internal fun build(): NativeTlsConfig {
		val serverName = this.serverName ?: throw IllegalArgumentException("server name must be specified")
		return NativeTlsConfig(
			serverName = serverName,
			verifyCertificates = this.verifyCertificates,
			trustAnchorsDer = this.trustAnchorsDer,
			soKeepAliveConfig = this.soKeepAliveConfig,
		)
	}
}

@ConsistentCopyVisibility
public data class NativeTlsConfig internal constructor(
	public val serverName: String,
	public val verifyCertificates: Boolean,
	public val trustAnchorsDer: List<ByteArray>,
	public val soKeepAliveConfig: SoKeepAliveConfig?,
)

public class SoKeepAliveConfigBuilder {
	public var idle: Duration? = null
	public var interval: Duration? = null
	public var probeCount: Int? = null

	public fun build(): SoKeepAliveConfig? {
		val idleTime = this.idle ?: return null
		val interval = this.interval ?: return null
		val probeCount = this.probeCount ?: return null
		return SoKeepAliveConfig(
			idle = idleTime,
			interval = interval,
			probeCount = probeCount,
		)
	}
}

@ConsistentCopyVisibility
public data class SoKeepAliveConfig internal constructor(
	public val idle: Duration,
	public val interval: Duration,
	public val probeCount: Int,
) {
	public companion object {
		public val Default: SoKeepAliveConfig =
			SoKeepAliveConfig(
				idle = 10.seconds,
				interval = 5.seconds,
				probeCount = 3,
			)
	}
}

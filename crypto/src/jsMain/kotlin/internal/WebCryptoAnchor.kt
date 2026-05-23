package io.natskt.crypto.internal

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.webcrypto.WebCrypto

@OptIn(ExperimentalStdlibApi::class)
@Suppress("DEPRECATION", "UNUSED")
@EagerInitialization
private val anchor: Unit =
	run {
		CryptoModuleMarker.provider = CryptographyProvider.WebCrypto
	}

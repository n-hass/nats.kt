package io.natskt.crypto.internal

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.providers.openssl3.Openssl3

@OptIn(ExperimentalStdlibApi::class)
@Suppress("DEPRECATION", "UNUSED")
@EagerInitialization
private val anchor: Unit =
	run {
		CryptoModuleMarker.provider = CryptographyProvider.Openssl3
	}

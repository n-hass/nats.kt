package io.natskt.crypto.internal

import dev.whyoleg.cryptography.CryptographyProvider

// Anchor the crypto provider so the compiler DCE doesn't strip it. Whyoleg's provider auto-registers
// itself via an @EagerInitialization, but that block _sometimes_ only survives DCE if something
// reachable from main references the provider's symbols. Without this
// anchor, CryptographyProvider.Default throws "No providers registered" on linux native targets.
internal object CryptoModuleMarker {
	var provider: CryptographyProvider? = null
}

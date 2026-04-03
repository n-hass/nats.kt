# About the crypto module 

NATS requires cryptographic operations (Ed25519 signing) to authenticate with a server using an NKey.

NATS.kt uses the [cryptography-kotlin](https://github.com/whyoleg/cryptography-kotlin) Multiplatform library to achieve this.

This library uses only bindings to native implementations of the cryptographic primitives, making it the most secure option, but it requires configuration of a Cryptographic Provider.

## Cryptographic Providers

NATS.kt automatically installs a compatible cryptographic provider with the `natskt-crypto` module. If you need to use a different provider, you should install it manually instead of the `natskt-crypto` module:

```kotlin
commonMain.dependencies {
	implementation("io.github.n-hass:natskt-core:{{ current_version }}")
	implementation("io.github.n-hass:natskt-jetstream:{{ current_version }}")
}

jvmMain.dependencies {
	implementation("dev.whyoleg.cryptography:cryptography-provider-jdk-bc:<cryptography-kotlin-version>")
}
```

The `natskt-crypto` module uses the `cryptography-provider-optimal` provider by default for all platforms, except:

- JVM uses BouncyCastle
- iOS/macOS uses CryptoKit

These providers support the required features for NATS.kt. 

# About the crypto module 

NATS requires cryptographic operations (Ed25519 signing) to authenticate with a server using an NKey.

NATS.kt uses the [cryptography-kotlin](https://github.com/whyoleg/cryptography-kotlin) Multiplatform library to achieve this.

This library uses only bindings to native implementations of the cryptographic primitives, making it the most secure option, but it requires configuration of a Cryptographic Provider.

## Cryptographic Providers

NATS.kt automatically installs a compatible cryptographic provider with the `natskt-crypto` module. The selected provider depends on the target:

| Target | Provider |
|---|---|
| JVM | BouncyCastle (`cryptography-provider-jdk-bc`) |
| Apple (iOS, macOS) | CryptoKit (`cryptography-provider-cryptokit`) |
| Linux native | OpenSSL 3 (`cryptography-provider-openssl3-api`, with prebuilt libs on Linux – see below) |
| JS / Wasm | Web Crypto (`cryptography-provider-webcrypto`) |

These providers support the required features for NATS.kt.

If you need to use a different provider, install it manually instead of the `natskt-crypto` module:

```kotlin
commonMain.dependencies {
	implementation("io.github.n-hass:natskt-core:{{ current_version }}")
	implementation("io.github.n-hass:natskt-jetstream:{{ current_version }}")
}

jvmMain.dependencies {
	implementation("dev.whyoleg.cryptography:cryptography-provider-jdk-bc:<cryptography-kotlin-version>")
}
```

## `natskt-crypto` vs `natskt-crypto-headless`

There are two flavours of the crypto module. They differ only on **Linux native** targets:

- **`natskt-crypto`** – pulls `cryptography-provider-openssl3-prebuilt-nativebuilds` on Linux native, which transitively contributes `openssl-libcrypto` as a link input. (It does **not** bring `openssl-libssl` – that's only needed for TLS, so consumers add it themselves via [ktor-native-tls](native-tls.md) if required.) This is the default choice for NKey signing on Linux native.
- **`natskt-crypto-headless`** – pulls only the OpenSSL 3 API bindings on Linux native. **No `libcrypto` on the link path.**

On JVM, Apple, JS, and Wasm targets the two modules are functionally identical – the split only matters where OpenSSL gets linked into a native binary.

`natskt-platform` bundles `natskt-crypto` (the full one). If you need the headless variant, install the modules individually rather than pulling `natskt-platform`.

### When to use `natskt-crypto-headless`

The headless variant exists because of **external** link-path conflicts, not anything internal to NATS.kt. The most common case is when another dependency on your classpath bundles its own OpenSSL – `ktor-client-curl` is the canonical example: it ships a `libssl` + `libcrypto` for the native targets it supports. Combining that with `natskt-crypto` results in duplicate-symbol errors at the native link step because both artifacts contribute `libcrypto`.

Swap to `natskt-crypto-headless` and the external library becomes the single source of OpenSSL on the link path:

```kotlin
nativeMain.dependencies {
    implementation("io.github.n-hass:natskt-core:{{ current_version }}")
    implementation("io.github.n-hass:natskt-jetstream:{{ current_version }}")
    implementation("io.github.n-hass:natskt-crypto-headless:{{ current_version }}")
    implementation("io.ktor:ktor-client-curl:<ktor-version>")
}
```

Other situations where headless is the right pick:

- You're linking against a system OpenSSL through your own linker options
- You want to pin a specific OpenSSL version / build that differs from the prebuilt one

See [Native TLS → Duplicate symbols](native-tls.md#duplicate-symbols) for the full walkthrough of the conflict and how to diagnose it.


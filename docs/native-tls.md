# Native TLS

> I tried to hide this complexity from you. I'm sorry. Now the best I can do is give you the options to make your builds not-flakey. 


The `ktor-native-tls` module provides TLS 1.2 and 1.3 for Ktor sockets on Kotlin/Native targets (Linux, macOS, iOS), where the JVM/Ktor CIO TLS engine isn't available. It binds OpenSSL through cinterop and delegates trust evaluation to platform-native APIs (Apple Security framework on Apple targets, OpenSSL on Linux).

In NATS.kt it slots in under `TcpTransport` automatically – drop it on the classpath of a Kotlin/Native target and `tlsRequired = true` (or a `tls://` URL) just works. It's published as a general Ktor TLS upgrade module, so it can be used outside NATS.kt as well.

## Install

```kotlin
nativeMain.dependencies {
	implementation("io.github.n-hass:natskt-platform:{{ current_version }}")
	implementation("io.github.n-hass:ktor-native-tls:{{ current_version }}") 
	// libssl is needed on every native target. natskt-platform pulls natskt-crypto, 
	// which on Linux native contributes libcrypto but NOT libssl. Apple targets need it too. 
	implementation("com.ensody.nativebuilds:openssl-libcrypto:3.6.2")
	implementation("com.ensody.nativebuilds:openssl-libssl:3.6.2")
}
```

Once on the classpath the module self-registers as the TLS upgrader for `TcpTransport`. There's no API call needed – set `tlsRequired = true` (or connect to a `tls://` URL) and the transport will hand the socket off to OpenSSL.

The OpenSSL libs on the link path are what actually make TLS work. The snippet above is the default for a project whose only OpenSSL-shipping dependency is `natskt-crypto`. **If you also depend on something else that bundles OpenSSL** (most commonly `ktor-client-curl`), the snippet above will clash – see [Duplicate symbols](#duplicate-symbols--link-errors-like) below.

## OpenSSL native libraries: bring your own

`ktor-native-tls` only declares the OpenSSL **headers** as a cinterop input. It deliberately does not declare `libssl.a` / `libcrypto.a` as link inputs. This is so you can pick the OpenSSL build that works for your project – typically one of:

- A prebuilt OpenSSL via the `com.ensody.nativebuilds` artifacts the module's headers come from (`openssl-libssl` + `openssl-libcrypto`)
- The OpenSSL transitively contributed by `natskt-crypto` on Linux native – see below
- The OpenSSL transitively contributed by **another** dependency on your classpath that bundles it (e.g. `ktor-client-curl` ships its own libssl + libcrypto on the native targets it supports)
- A system OpenSSL you wire up yourself through Kotlin/Native linker options

There's no central registry of which Kotlin/Native artifacts contribute OpenSSL. If you hit a link-time error, `gradle :<your-module>:dependencies` is the source of truth – see the failure-mode walkthroughs below.

### How `natskt-crypto` and `natskt-crypto-headless` interact

NATS.kt ships two crypto artifacts. The difference matters only at the native link step:

| Artifact | Linux native | Apple native | JVM / JS / Wasm |
|---|---|---|---|
| `natskt-crypto` | Pulls `cryptography-provider-openssl3-prebuilt-nativebuilds`, which contributes `openssl-libcrypto` only – `libssl` is **not** transitively linked | CryptoKit only, no OpenSSL libs | platform provider, no OpenSSL libs |
| `natskt-crypto-headless` | Only `cryptography-provider-openssl3-api` (bindings only), **no** OpenSSL libs | CryptoKit only, no OpenSSL libs | platform provider, no OpenSSL libs |

`natskt-platform` includes `natskt-crypto` (the full one), so platform consumers on Linux native already have `libcrypto` on the link path – but still need `libssl` added explicitly for `ktor-native-tls` to link.

`natskt-crypto-headless` exists because of external link-path clashes (see [Duplicate symbols](#duplicate-symbols--link-errors-like)), not because of anything internal to NATS.kt. Reach for it when another dependency in your build already ships OpenSSL and the duplicates are coming from there.

If nothing on your link path provides OpenSSL the native link step will fail. If two things provide it the link step will fail differently.

### The two failure modes

##### **Missing symbols** – link errors like:

```
Undefined symbols: _SSL_CTX_new, _SSL_new, _BIO_new_fd, _X509_STORE_add_cert, ...
```

Nothing on the link path is providing OpenSSL. Add the prebuilt libraries to the source set that needs them:

```kotlin
nativeMain.dependencies {
    implementation("io.github.n-hass:ktor-native-tls:{{ current_version }}")
    implementation("com.ensody.nativebuilds:openssl-libssl:3.6.2")
    implementation("com.ensody.nativebuilds:openssl-libcrypto:3.6.2")
}
```

Use the OpenSSL version that matches/close to the headers `ktor-native-tls` was built against (currently 3.6.2). Mixing major OpenSSL versions across headers and libs will either fail to link or produce subtle runtime breakage.

##### **Duplicate symbols** – link errors like:

```
duplicate symbol '_X509_STORE_add_cert' in: libcrypto.a(x509_lu.o) and libcrypto.a(x509_lu.o)
duplicate symbol '_EVP_DigestInit_ex' in: libcrypto.a(digest.o) and libcrypto.a(digest.o)
```

Two different dependencies are each contributing their own `libcrypto` (or, less often, `libssl`) to the link. There is one common scenario:

**Another dependency on your classpath bundles its own OpenSSL.** The most common case is `ktor-client-curl`, which ships its own `libssl` and `libcrypto` on the native targets it supports. Anything pulling that in (or any other artifact that bundles OpenSSL) collides with both `natskt-crypto`'s `libcrypto` and any explicit `openssl-libssl` / `openssl-libcrypto` you've added for `ktor-native-tls`.

```kotlin
nativeMain.dependencies {
    implementation("io.github.n-hass:natskt-core:{{ current_version }}")
    implementation("io.github.n-hass:natskt-jetstream:{{ current_version }}")
    implementation("io.github.n-hass:natskt-crypto-headless:{{ current_version }}")  // no OpenSSL libs
    implementation("io.github.n-hass:ktor-native-tls:{{ current_version }}")
    implementation("io.ktor:ktor-client-curl:<ktor-version>")  // brings its own libssl + libcrypto
    // no explicit openssl-libssl / openssl-libcrypto here – ktor-client-curl provides them
}
```

This works because `ktor-native-tls` only needs OpenSSL **symbols** on the link path. It does not care which artifact contributes them, as long as the version is compatible with the headers it was built against (OpenSSL 3.x – Ktor's curl typically ships a compatible version).

**`natskt-crypto` plus an explicit `openssl-libcrypto`** on a source set that applies to Linux will **NOT** cause this, as they use the same `com.ensody.nativebuilds` artifact. In fact, this resolution compatability is what gives you the ability to pin your own `libssl` and `libcrypto` versions from `com.ensody.nativebuilds` and have the native code link against that.

Inspect `gradle :<your-module>:dependencies` and look for any artifact whose name contains `openssl`, `libssl` or `libcrypto`, or any non-natskt dependency that bundles OpenSSL (curl-based engines, gRPC implementations, etc.). If you see more than one source contributing the same lib, that's your duplicate.

### Quick checklist

- **No external OpenSSL-shipping dependency** (no `ktor-client-curl`, etc.) **and using `natskt-crypto` / `natskt-platform`**: Add **both** `openssl-libssl` and `openssl-libcrypto` to `nativeMain`.
- **No external OpenSSL-shipping dependency and using `natskt-crypto-headless`**: add **both** `openssl-libssl` and `openssl-libcrypto` to `nativeMain`.
- **External dependency already bundles OpenSSL (e.g. `ktor-client-curl`)**: drop `natskt-platform`, swap `natskt-crypto` for `natskt-crypto-headless` and do **not** add explicit `openssl-libssl` / `openssl-libcrypto`. Let the external library be the single source.
- **Apple-only target with no Linux**: always add **both** `openssl-libssl` and `openssl-libcrypto`. Neither crypto module contributes OpenSSL on Apple.

If you're unsure what's on the link path, run `gradle :<your-module>:dependencies` and search for `openssl`, `libssl`, `libcrypto`, and `curl`. Anything with `-prebuilt-nativebuilds`, `libssl`, or `libcrypto` in the artifact name contributes link inputs; anything ending in `-api` only contributes Kotlin bindings.

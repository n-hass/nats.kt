# 0.9.0 release 🚀

A big one. NATS.kt 0.9.0 brings the cryptography-kotlin migration that was on the
1.0.0-rc.1 track, a full Object Store implementation, a major expansion of the KV
API, native TLS support for Kotlin/Native targets, and a long list of core/JetStream
improvements and fixes.

## Highlights

### Production-ready cryptography

NKey signing now uses [`cryptography-kotlin`](https://github.com/whyoleg/cryptography-kotlin)
with platform-native crypto providers. This was a the final blocker for safe production use
and supersedes the previous lightweight Ed25519 implementation that shipped in 0.1.0–0.8.0.

A new `natskt-crypto` module is published, and a new `natskt-platform` all-in-one
artifact is available that bundles core, jetstream, nkeys, transports and crypto so
you can depend on a single coordinate:

```kotlin
commonMain.dependencies {
    implementation("io.github.n-hass:natskt-platform:0.9.0")
}
```

If you prefer modular dependencies, add `natskt-crypto` alongside the other modules
you used pre-0.9.0 to enable nkey-based authentication.

### Object Store

JetStream Object Store is now supported, including:

- A managed `ObjectStore` interface for put/get/delete and metadata
- An `ObjectStoreManager` for create/update/delete/list of object buckets
- Basic streaming support (WIP, more to come)

### Key-Value: greatly expanded

The KV API has been substantially expanded:

- `watch(...)` and `watchAll(...)` returning a `Flow<KeyValueEntry>`
- `history(key)` to read the full revision history of a key
- `keys(...)` and `consumeKeys(...)` with single- or multi-filter overloads
- `purgeDeletes(...)` for compacting tombstones
- Get-by-revision now verifies the supplied key matches the revision
- Bucket management: expanded create/update configuration options on
  `KeyValueManager` and a configuration builder DSL

### Native TLS

A new `natskt-network-tls` module provides TLS 1.2 & 1.3 in `TcpTransport` 
on Kotlin/Native targets (Linux, macOS, iOS), where
the JVM/Ktor CIO TLS engine is unavailable.

Certificate validation uses platform-native trust stores (Apple Security framework
on Apple targets, OpenSSL on Linux).

But be warned: this is experimental, and been mostly created by an LLM programming tool.
Although care has been taken to ensure it functions correctly on the happy-path, there
are likely correctness and security bugs in there somewhere. Hence; THIS IS AN EXPERIMENT.

## Core protocol & client

- `client.serverInfo` is now exposed as a `StateFlow<ServerInfo?>` on `NatsClient`
  so consumers can observe server identity and capabilities reactively
- Last server `-ERR` is now surfaced on `ConnectionState`
- `unsubscribe(maxMessages = …)` is now supported
- No-responders is now configurable, and **defaults to `true`**
- UTF-8 subjects can be enabled via client configuration
- Subject and subject-token validation now aligns with `nats.go`

## JetStream

- New stream configuration options
- `getFirstMessage` on streams
- `PullConsumer.consume()` returns a cold `Flow` for long-lived pull
  consumption (in addition to bounded `fetch`)
- Pull fetch supports priority groups and pending filters
- New consumer configuration options and ack-protocol options
- KV bucket management expanded (see Highlights)
- Object Store added (see Highlights)

## Breaking changes

The following changes are source-incompatible. Most are small migrations.

1. **`KeyValueBucket` is now an interface** with config and status exposed as
   `StateFlow`s instead of methods returning a snapshot.

1. **JetStream and KeyValueManager require the name as a parameter on
   updates**, and the configuration builder passed to update is seeded with the
   current values rather than starting empty. The name is now enforced to match
	 the function param of the update. Builders that previously assumed
   an empty starting state should be reviewed. The new `ObjectStoreManager` also
	 behaves the same.

1. **The configurable protocol-engine latency ceiling has been removed.** This
   was the underlying cause of dropped messages under a timeout race. There is
   no replacement — the engine no longer needs the ceiling to behave correctly.
   Remove any references to that configuration option.

1. **`io.natskt.api.Connection` has been removed.** It was an unused holdover;
   use `NatsClient` directly.

1. **No-responders defaults to `true`.** If you relied on the previous default
   (`false`), set it explicitly in your client configuration.

## Bug fixes

- TCP transport now maintains a single `SelectorManager` for correct native
  thread management, and the selector is now closed on transport shutdown
- Several races to `SUB` fixed by switching to `onSubscribe` for setup
- KV watch consumers are now properly torn down when the watch flow is cancelled
- KV config update on the builder now seeds from the existing config and
  enforces the bucket name
- Pull consumer now surfaces status messages on `fetch` instead of silently
  dropping them, and filters out responses from stale fetch requests
- Duration serialization errors fixed (TTL headers)
- TTL header is now serialised as a Go-style duration string (server-compatible)
- `close()` on a client that was never opened no longer throws

## Dependencies & tooling

- Kotlin 2.3.20 (minimum supported Kotlin: 2.2)
- Ktor 3.4.2

## Thanks

**Full Changelog:** https://github.com/n-hass/nats.kt/compare/v0.8.0...v0.9.0

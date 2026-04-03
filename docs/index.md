# NATS.kt

`NATS.kt` is a Kotlin Multiplatform client for the [NATS](https://nats.io) messaging system. The repository ships a coroutine-first core client, optional JetStream support, example applications, and a shared integration test harness used across supported targets.

## What This Site Covers

- Installing the published artifacts from Maven Central
- Connecting with the core NATS client over TCP or WebSocket
- Working with JetStream streams, consumers, and key-value buckets
- Running the examples and contributing locally

## Published Modules

Most consumers only need the first one or two artifacts:

| Artifact                            | Purpose                                                 |
|-------------------------------------|---------------------------------------------------------|
| `io.github.n-hass:natskt-platform`  | All-in-one dependency to have a working installation    |
| `io.github.n-hass:natskt-core`      | Core client without JetStream for minimal installations |
| `io.github.n-hass:natskt-jetstream` | JetStream support with the core client                  |
| `io.github.n-hass:natskt-crypto`    | Cryptography libraries for KMP used by NATS.kt          |

## Supported Targets

The repository currently builds these explicit Kotlin Multiplatform targets:

| Target family | Variants |
| --- | --- |
| JVM | `jvm` |
| JavaScript | `jsBrowser`, `jsNode` |
| Wasm | `wasmJsBrowser`, `wasmJsNode` |
| Apple | `iosArm64`, `iosSimulatorArm64`, `macosArm64` |
| Linux | `linuxX64`, `linuxArm64` |

The JVM artifacts target Java 17 bytecode. 

## Next Steps

- Start with [Getting Started](getting-started.md) for installation and a minimal connection example.
- Use [Core Client](core-client.md) when you need configuration details and transport selection.
- Use [JetStream](jetstream.md) when you want streams, consumers, or key-value buckets.

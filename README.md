# NATS.kt 🚀

> **A fully multiplatform, Ktor-based NATS client in pure Kotlin**

[![Kotlin](https://img.shields.io/badge/kotlin-multiplatform-blue.svg?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-active%20development-yellow.svg)](#development-status)
[![CI Tests](https://github.com/n-hass/nats.kt/actions/workflows/tests.yml/badge.svg?branch=main)](https://github.com/n-hass/nats.kt/actions/workflows/tests.yml)

**NATS.kt** is a high-performance NATS client built specifically for Kotlin Multiplatform. Powered by Ktor's networking stack, it brings NATS messaging to every platform Kotlin supports - including servers, mobile apps, browsers and native applications.

- **Universal Platform Support**: Deploy your NATS-powered applications anywhere Kotlin runs
- **Transport Flexibility**: TCP and WebSocket transports supported
- **Coroutines**: Coroutine-based API
- **DSL-style usage**: Clean Kotlin API with a configuration DSL
- **Built on Ktor**: Leverage Ktor's stability for networking capabilities and performance

## 🎯 Supported Platforms

| Platform | Support |
|----------|--------|
| JVM | Full |
| Android | Full |
| iOS (ARM64) | Full |
| iOS Simulator (ARM64) | Full |
| macOS (ARM64) | Full |
| Linux (x64) | Full |
| Linux (ARM64) | Full |
| JavaScript (Browser) | Full |
| JavaScript (Node.js) | Full |
| WasmJS | Full |

**Minimum JVM**: 17

**Minimum Kotlin**: 2.1

## 🏃 Quick Start

### Installation

Gradle:

```kotlin
commonMain.dependencies {
	implementation("io.github.n-hass:natskt-core:0.7.0")
	// and if you want jetstream:
	implementation("io.github.n-hass:natskt-jetstream:0.7.0")
}
```

### Basic Usage

```kotlin
import io.natskt.NatsClient

// Simple connection
val client = NatsClient("nats://localhost:4222")
client.connect()

// Advanced configuration
val client = NatsClient {
    server = "nats://localhost:4222"
    transport = TcpTransport
}

val client = NatsClient {
    server = "ws://localhost:4222"
    transport = WebSocketTransport.Factory(CIO)
}

// Subscribe to messages
val subscription = client.subscribe("orders.new")
subscription.messages.collect { message ->
    println("Received: ${message.data}")
}
```

## 📋 Feature Coverage

NATS.kt is under **active development**.

| Feature                               | Status | Notes                    |
|---------------------------------------|--|------------------------------|
| **Core Protocol**                     |  |                              |
| Multiplatform TCP/WebSocket transport |✅ |                              |
| Authentication                        |✅ | * See security notice below! |
| Publish/Subscribe                     |✅ |                              |
| Request/Reply                         |✅ |                              |
| **Jetstream**                         |  |                              |
| Basic API client                      |✅ |                              |
| Pull consumer                         |✅ |                              |
| Push consumer                         |🟠 | Functional, missing server liveness timeout  |
| Key-Value Store                       |✅ |                              |
| Object Store                          |❌ |                              |
| **JetStream Management**              |  |                              |
| Streams                               | ✅ |                              |
| Consumers                             | ✅ |                              |

**Legend**: ✅ Complete | 🟠 Partially Complete | 🚧 In Progress |  ❌ Planned

## 🧪 Examples

Check out our [examples directory](examples/) for comprehensive usage examples:
- [Basic Subscribe](examples/subscribe/) - Simple message subscription
- [Requests](examples/request/) - Making requests with core NATS
- [JetStream](examples/jetstream/) - Using the JetStream management API and binding to a consumer
- [Custom Credentials](examples/custom-credentials/) - Providing custom authentication credentials for use with auth-callout

More coming soon!

## 🛡️ Security notice

The upcoming 1.0.0 stable release of NATS.kt will include a new platform-native cryptography library that was not used in pre-release versions (0.1.0 – 0.7.0).

If you are concerned about potential security vulnerabilities when using an un-attested Ed25519 implementation, please use the 1.0.0-rc.1 pre-release:

```kotlin
commonMain.dependencies {
	implementation("io.github.n-hass:natskt-platform:1.0.0-rc.1")
}
```

## 📄 License

NATS.kt is released under the [Apache 2.0 License](LICENSE).

## 🙏 Acknowledgments

- **[Dimensional-Fun NATS.kt](https://github.com/dimensional-fun/nats.kt/tree/main)** - For some foundational code and inspiration
- **[Ktor](https://ktor.io)** - The networking foundation

---

**Ready to build the next generation of distributed Multiplatform applications?** Star ⭐ this repo and join our community!

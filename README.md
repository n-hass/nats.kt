# NATS.kt 🚀

> **A fully multiplatform, Ktor-kased NATS client in pure Kotlin**

[![Kotlin](https://img.shields.io/badge/kotlin-multiplatform-blue.svg?logo=kotlin)](https://kotlinlang.org/docs/multiplatform.html)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.20-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Ktor](https://img.shields.io/badge/powered%20by-ktor-blue.svg)](https://ktor.io)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](LICENSE)
[![Status](https://img.shields.io/badge/status-active%20development-yellow.svg)](#development-status)

**NATS.kt** is a high-performance NATS client built specifically for Kotlin Multiplatform. Powered by Ktor's networking stack, it brings NATS messaging to every platform Kotlin supports - including servers, mobile apps, browsers and native applications.

## ✨ Why NATS.kt?

- **🌐 Universal Platform Support**: Deploy your NATS-powered applications anywhere Kotlin runs
- **🏗️ Transport Flexibility**: TCP and WebSocket transports supported 
- **🤝 Coroutines**: Coroutine-based API for idiomatic reactive programming
- **🔧 Developer-First**: Clean, idiomatic Kotlin API with DSL configuration
- **⚡ Built on Ktor**: Leverage Ktor's stability for networking capabilities and performance

## 🎯 Supported Platforms

| Platform | Status |
|----------|--------|
| JVM | ✅ Full Support |
| Android | ✅ Full Support |
| iOS (ARM64) | ✅ Full Support |
| iOS Simulator (ARM64) | ✅ Full Support |
| macOS (ARM64) | ✅ Full Support |
| Linux (x64) | ✅ Full Support |
| Linux (ARM64) | ✅ Full Support |
| JavaScript (Browser) | ✅ Full Support |
| JavaScript (Node.js) | ✅ Full Support |
| WebAssembly | ✅ Full Support |

## 🚀 Quick Start

### Installation

Gradle:

```kotlin
commonMain.dependencies {
	implementation("io.github.n-hass:natskt-core:0.1.1")
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
    println("Received: ${message.payload}")
}
```

## 📋 Feature Coverage

NATS.kt is under **active development**.

| Feature                               | Status | Notes                         |
|---------------------------------------|---|-------------------------------|
| **Core Protocol**                     |   |                               |
| Multiplatform TCP/WebSocket transport | ✅ |                               |
| Authentication                        | ✅ | * See security notice at end! |
| Publish/Subscribe                     | ✅ |                               |
| Request/Reply                         | ✅ |                               |
| **Jetstream**                         |   |                               |
| Basic API                             | 🚧 | Foundational client + pull    |
| Pull consumer                         | 🚧 | Core fetch/ack implementation |
| Push consumer                         | ❌ |                               |
| Key-Value Store                       | ❌ |                               |
| Object Store                          | ❌ |                               |
| **JetStream Management**              |   |                               |
| Streams                               | ❌ |                               |
| Consumers                             | ❌ |                               |

**Legend**: ✅ Complete | 🚧 In Progress | ❌ Planned

My priority right now is to reach a good level of stability and correctness with all JetStream consumer features,
then to go back and address any performance optimisations that can be made.

## 🧪 Examples

Check out our [examples directory](examples/) for comprehensive usage examples:
- [Basic Subscribe](examples/subscribe/) - Simple message subscription

More coming soon!

## 🛡️ Security

Please do not rely on the NKEY/Creds authentication features included in this client in a production environment. Although functionally correct, some cryptographic operations have not been formally verified and may have vulnerabilities. 

The NKEY implementation uses an Ed25519 algorithm implementation from an [un-attested library](https://github.com/andreypfau/curve25519-kotlin). This is used to sign authentication requests by the server on connect.

I plan to move the NKEY implementation to use [cryptography-kotlin](https://github.com/whyoleg/cryptography-kotlin), a multiplatform binding to various other platform-native attested libraries, like JCA, OpenSSL and WebCrypto. This is waiting on the features being developed in that library upstream.

The rest of the secure operations, including [secure-random](https://github.com/whyoleg/cryptography-kotlin) and TLS for TCP and WebSocket transports, use implementations which delegate to platform-native libraries and should not pose a notable security risk. 

## 📄 License

NATS.kt is released under the [Apache 2.0 License](LICENSE).

## 🙏 Acknowledgments

- **[Dimensional-Fun NATS.kt](https://github.com/dimensional-fun/nats.kt/tree/main)** - For some foundational code and inspiration
- **[Ktor](https://ktor.io)** - The networking foundation

---

**Ready to build the next generation of distributed Multiplatform applications?** Star ⭐ this repo and join our community!

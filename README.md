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
- **🛡️ Coroutines**: Coroutine-based API for idiomatic reactive programming
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

NATS.kt is not quite usable yet, and so is not published to maven. 
You can clone this repo and use it as an [includeBuild](https://docs.gradle.org/current/userguide/composite_builds.html)
to use it while features are still in development.

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

| Feature                               | Status | Notes |
|---------------------------------------|---|-----|
| **Core Protocol**                     |   |     |
| Multiplatform TCP/WebSocket transport | ✅ |     |
| Authentication                        | 🚧 |     |
| Publish/Subscribe                     | ✅ |     |
| Request/Reply                         | 🚧 | Up next |
| **Jetstream**                         |   |     |
| Basic API                             | ❌ |     |
| Key-Value Store                       | ❌ |     |
| Object Store                          | ❌ |     |
| **JetStream Management**              |   |     |
| Streams                               | ❌ |     |
| Consumers                             | ❌ |     |

**Legend**: ✅ Complete | 🚧 In Progress | ❌ Planned

## 🛠️ Development Environment

We use **Nix** for a consistent, reproducible development environment.

### Using Nix

**Option 1: With direnv (Recommended)**
```bash
# Install direnv if you haven't already
# Then simply cd into the project directory
cd nats.kt
direnv allow
# direnv will automatically load the development environment
```

**Option 2: Manual Nix shell**
```bash
cd nats.kt
nix develop
```

### What's Included

Our Nix environment provides:
- **JDK 21** - Latest LTS Java for optimal performance
- **Gradle** - Build and dependency management
- **NATS Server** - Local NATS server for testing
- **NATS CLI** - Command-line tools for NATS administration
- **Pre-commit hooks** - Code formatting and conventional commits

## 🧪 Examples

Check out our [examples directory](examples/) for comprehensive usage examples:
- [Basic Subscribe](examples/subscribe/) - Simple message subscription

More coming soon!

## 🔧 Building

```bash
# Build all targets
gradle build

# Run tests
gradle test

# Apply code formatting
gradle spotlessApply
```

## 🤝 Contributing

We welcome contributions! NATS.kt is in active development and there are many opportunities to help:

1. **Check out our issues** - Look for "good first issue" labels
2. **Review the feature coverage** - Pick an unimplemented feature
3. **Improve documentation** - Help make NATS.kt more accessible
4. **Add examples** - Show how to use NATS.kt in real scenarios

### Development Workflow

1. Fork the repository
2. Use our Nix development environment (`nix develop` or direnv)
3. Make your changes following our code style
4. Add tests for new functionality
5. Ensure all tests pass (`gradle test`)
6. Submit a pull request

### Commit Convention

We use [Conventional Commits](https://www.conventionalcommits.org/). Examples:
- `feat(core): add request/reply support`
- `fix(transport): handle connection timeouts`
- `docs(readme): update installation instructions`

## 📄 License

NATS.kt is released under the [Apache 2.0 License](LICENSE).

## 🙏 Acknowledgments

- **[Dimensional-Fun NATS.kt](https://github.com/dimensional-fun/nats.kt/tree/main)** - For some foundational code and inspiration
- **[Ktor](https://ktor.io)** - The networking foundation

---

**Ready to build the next generation of distributed Multiplatform applications?** Star ⭐ this repo and join our community!
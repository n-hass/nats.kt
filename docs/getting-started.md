# Getting Started

## Requirements

- Java 17 or newer for JVM clients
- Kotlin 2.2.0 or newer

## Install The Client

Add the platform artifact to `commonMain`:

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.n-hass:natskt-platform:{{ current_version }}")
        }
    }
}
```

### Custom dependency configuration

The library is built to split for smaller installations when needed.

- The `core` artifact is the minimum required for basic NATS usage.
- The `jetstream` artifact adds JetStream support.
- The `crypto` artifact adds support for connecting to NATS servers that require authentication

The `platform` artifact is a convenience that includes all 3 of the above, and so is equivalent to: 

```kotlin
commonMain.dependencies {
    implementation("io.github.n-hass:natskt-core:{{ current_version }}")
    implementation("io.github.n-hass:natskt-jetstream:{{ current_version }}")
    implementation("io.github.n-hass:natskt-crypto:{{ current_version }}")
}
```

To read more on the crypto artifact, see [Cryptography](cryptography.md).


## Connect To A Server

```kotlin
import io.natskt.NatsClient
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val client = NatsClient("nats://localhost:4222")

    client.connect().getOrThrow()

    client.publish(
        subject = "orders.created",
        message = """{"id":42}""".encodeToByteArray(),
    )

    val reply = client.request(
        subject = "orders.lookup",
        message = "42".encodeToByteArray(),
    )

    println(reply.data?.decodeToString())

    client.disconnect()
}
```

## Builder-Style Configuration

Use the DSL when you need to configure client options, such as transport or authentication settings:

```kotlin
import io.natskt.NatsClient
import io.natskt.api.Credentials
import io.natskt.client.transport.TcpTransport

val client = NatsClient {
    server = "nats://localhost:4222"
    transport = TcpTransport
    authentication = Credentials.Password(
        username = "demo",
        password = "secret",
    )
    maxReconnects = 5
}
```

## Where To Go Next

- Update `current_version` in `mkdocs.yml` after each release to refresh every versioned docs snippet.
- [Core Client](core-client.md) covers lifecycle, transports, authentication, and request/reply behavior.
- [JetStream](jetstream.md) covers streams, consumers, publishes, and key-value buckets.
- [Examples](https://github.com/n-hass/nats.kt/tree/main/examples) for runnable samples of different things you can do with NATS.kt.

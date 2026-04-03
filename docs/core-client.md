# Core Client

## Connection Lifecycle

- `connect()` suspends until the connection succeeds or fails and returns `Result<Unit>`.
- `disconnect()` closes the connection and transport.
- `drain(timeout)` unsubscribes active subscriptions and flushes the client before returning.
- `flush()` forces pending operations to the transport.
- `ping()` updates round-trip timing in `connectionState`.

Typical startup code:

```kotlin
client.connect().getOrThrow()
```

## Publish And Subscribe

```kotlin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val client = NatsClient("nats://localhost:4222")
    client.connect().getOrThrow()

    val subscription = client.subscribe("orders.created", eager = false)

    launch {
        client.publish("orders.created", "hello".encodeToByteArray())
        delay(250)
        subscription.unsubscribe()
    }

    subscription.messages.collect { message ->
        println(message.data?.decodeToString())
    }

    client.disconnect()
}
```

`Subscription.messages` is a `Flow<Message>`. 

By default, subscriptions automatically subscribe when you start collecting the flow, and unsubscribe when the last collector disappears.

This can be configured by the `eager` and `unsubscribeOnLastCollector` parameters on `NatsClient.subscribe`.

## Transport Selection

The builder chooses a platform-default transport when you do not provide one explicitly. On supported native and JVM targets that usually means TCP.

Explicit TCP:

```kotlin
import io.natskt.client.transport.TcpTransport

val client = NatsClient {
    server = "nats://localhost:4222"
    transport = TcpTransport
}
```

Explicit WebSocket transport:

```kotlin
import io.ktor.client.engine.cio.CIO
import io.natskt.client.transport.WebSocketTransport

val client = NatsClient {
    server = "ws://localhost:4222"
    transport = WebSocketTransport.Factory(CIO)
}
```

## Authentication

The client accepts `Credentials` through `authentication`.

Username and password:

```kotlin
import io.natskt.api.Credentials

val client = NatsClient {
    server = "nats://localhost:4222"
    authentication = Credentials.Password(
        username = "demo",
        password = "secret",
    )
}
```

Creds file content:

```kotlin
val credsFileContent = loadCredsFile()

val client = NatsClient {
    server = "nats://localhost:4222"
    authentication = Credentials.File(credsFileContent)
}
```

Custom provider (eg when using server auth-callout and need to manipulate exactly what is sent to the server):

```kotlin
import io.natskt.api.AuthPayload
import io.natskt.api.AuthProvider
import io.natskt.api.Credentials

val client = NatsClient {
    server = "nats://localhost:4222"
    authentication = Credentials.Custom(
        AuthProvider { info ->
            AuthPayload(
                jwt = "jwt",
                nkey = "public-nkey",
                signature = signNonce("seed", info),
            )
        },
    )
}
```

## Configuration Reference

These are the main fields exposed by `ClientConfigurationBuilder`:

| Property | Purpose |
| --- | --- |
| `server` / `servers` | One or more server URLs |
| `authentication` | `Credentials` implementation for login |
| `inboxPrefix` | Prefix used for request/reply inboxes |
| `maxReconnects` | Reconnect attempt limit after disconnect |
| `connectTimeout` | Maximum handshake time |
| `reconnectDebounce` | Delay between reconnect attempts |
| `maxControlLineBytes` | Maximum supported control line size |
| `maxPayloadBytes` | Maximum message payload size |
| `operationBufferCapacity` | Number of pending outgoing operations allowed |
| `writeBufferLimitBytes` | Buffered byte threshold before flush |
| `writeFlushInterval` | Maximum write latency ceiling |
| `maxParallelRequests` | Optional cap for concurrent request/reply calls |
| `tlsRequired` | Force TLS negotiation |
| `transport` | Transport factory override |
| `scope` | Custom coroutine scope for client jobs |

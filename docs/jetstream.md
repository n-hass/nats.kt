# JetStream

JetStream support lives in `io.github.n-hass:natskt-jetstream`. It builds on an existing `NatsClient` connection and exposes stream usage and management, consumer usage and management, and key-value buckets.

If you are **not** using the `natskt-platform` module, add the following to your `build.gradle.kts` or `build.gradle`

```kotlin
commonMain.dependencies {
    implementation("io.github.n-hass:natskt-jetstream:{{ current_version }}")
}
```

## Create A JetStream Client

```kotlin
import io.natskt.NatsClient
import io.natskt.jetstream.JetStreamClient

val client = NatsClient("nats://localhost:4222")
client.connect().getOrThrow()

val js = JetStreamClient(client)
```

## Manage Streams

```kotlin
val stream = js.manager.createStream {
    name = "orders"
    subjects = mutableListOf("orders.>")
}

println(stream.info.value?.config?.name)
```

The manager also exposes update, delete, purge, listing, consumer operations, and direct message lookup APIs.

## Publish With Acknowledgement

```kotlin
val ack = js.publish(
    subject = "orders.created",
    message = """{"id":42}""".encodeToByteArray(),
)

println(ack.seq)
```

## Bind To A Consumer

Attach to an existing consumer:

```kotlin
import io.natskt.jetstream.api.consumer.SubscribeOptions

val consumer = js.subscribe(
    SubscribeOptions.Attach(
        streamName = "orders",
        consumerName = "orders-worker",
        manualAck = true,
    ),
)
```

Create or update one through the manager first:

```kotlin
val info = js.manager.createOrUpdateConsumer("orders") {
    name = "orders-worker"
    deliverSubject = "orders.consumer"
}

val consumer = js.subscribe(
    SubscribeOptions.Attach("orders", info.name),
)
```

For push consumers, collect `messages` from the returned consumer and acknowledge manually when you enable `manualAck`.

## Key-Value Buckets

Create a bucket:

```kotlin
val bucket = js.keyValueManager.create {
    bucket = "profiles"
}
```

Use a bucket:

```kotlin
js.keyValue("profiles").use { bucket ->
    bucket.put("alice", "admin".encodeToByteArray())

    val entry = bucket.get("alice")
    println(entry.value.decodeToString())
}
```

`KeyValueBucket` keeps an internal request subscription alive. Close it when you are finished, or wrap it in `use`.

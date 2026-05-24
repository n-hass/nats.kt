package io.natskt

import io.ktor.utils.io.core.toByteArray
import io.natskt.client.transport.TcpTransport
import io.natskt.api.Credentials
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.github.oshai.kotlinlogging.DirectLoggerFactory
import io.github.oshai.kotlinlogging.Level
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.ktor.utils.io.readText
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
fun main(): Unit = runBlocking {
	KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
	KotlinLoggingConfiguration.direct.logLevel = Level.TRACE

	val credsPath = getenv("NATS_CREDS")?.toKString()?.takeIf { it.isNotBlank() }
	val server = getenv("NATS_SERVER")?.toKString()?.takeIf { it.isNotBlank() } ?: "nats://localhost"

    val c = NatsClient {
		this.server = server
        transport = TcpTransport
		inboxPrefix = "_INBOX.me."
		if (credsPath != null) {
			val source = SystemFileSystem.source(Path(credsPath))
			authentication = Credentials.File(
				source.buffered().use { it.readText() }
			)
		}
    }

	c.connect().getOrThrow()

	val response = c.subscribe("test.hi").messages.collect {
		println("got: ${it.data?.decodeToString()}")
	}

	println("complete")
}

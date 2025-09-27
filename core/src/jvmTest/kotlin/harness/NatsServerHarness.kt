package harness

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class NatsServerHarness : AutoCloseable {
	private val port: Int = ServerSocket(0).use { it.localPort }
	private val process: Process = startProcess()
	private val outputLines = mutableListOf<String>()
	private val stdoutReader: Thread = consumeOutput(process.inputStream.bufferedReader())

	val uri: String
		get() = "nats://127.0.0.1:$port"

	val logs: List<String>
		get() = synchronized(outputLines) { outputLines.toList() }

	init {
		waitForReady()
	}

	private fun startProcess(): Process {
		val builder = ProcessBuilder("nats-server", "-DV", "-a", "127.0.0.1", "-p", port.toString())
		builder.redirectErrorStream(true)
		return builder.start()
	}

	private fun consumeOutput(reader: BufferedReader): Thread =
		thread(start = true, isDaemon = true, name = "nats-server-stdout") {
			try {
				reader.useLines { lines ->
					lines.forEach { line ->
						synchronized(outputLines) { outputLines += line }
					}
				}
			} catch (_: IOException) {
				// Stream closed when process exits; safe to ignore.
			}
		}

	private fun waitForReady() {
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
		while (System.nanoTime() < deadline) {
			if (!process.isAlive) {
				throw IllegalStateException("nats-server exited early with ${process.exitValue()}")
			}

			try {
				Socket().use { socket ->
					socket.connect(InetSocketAddress("127.0.0.1", port), 200)
					return
				}
			} catch (_: Exception) {
				Thread.sleep(50)
			}
		}
		throw IllegalStateException("Timed out waiting for nats-server to start")
	}

	override fun close() {
		process.destroy()
		if (!process.waitFor(3, TimeUnit.SECONDS)) {
			process.destroyForcibly()
			process.waitFor(3, TimeUnit.SECONDS)
		}
		stdoutReader.join(500)
	}
}

suspend fun waitForLog(
	server: NatsServerHarness,
	predicate: (String) -> Boolean,
) {
	withTimeout(5_000) {
		while (true) {
			if (server.logs.any(predicate)) return@withTimeout
			delay(50)
		}
	}
}

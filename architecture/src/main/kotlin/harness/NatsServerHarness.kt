@file:OptIn(ExperimentalUuidApi::class)

package harness

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.contracts.ExperimentalContracts
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class NatsServerHarness(
	private val enableJetStream: Boolean = true,
) : AutoCloseable {
	private val port: Int = ServerSocket(0).use { it.localPort }
	private val tmpDir = Files.createTempDirectory("nats") ?: Path.of("/tmp/nats-test/${Uuid.random()}")
	private val logFile =
		Path
			.of("build/test-results/logs/nats.out")
			.also {
				Files.createDirectories(it.parent)
				Files.deleteIfExists(it)
				Files.createFile(it)
			}.toFile()

	private val process: Process = startProcess()
	private val outputLines = mutableListOf<String>()
	private val stdoutReader: Thread = consumeOutput(process.inputStream.bufferedReader(), logFile)

	val uri: String
		get() = "nats://127.0.0.1:$port"

	val logs: List<String>
		get() = synchronized(outputLines) { outputLines.toList() }

	init {
		waitForReady()
	}

	private fun startProcess(): Process {
		val command =
			mutableListOf(
				"nats-server",
				"-DV",
			).apply {
				if (enableJetStream) {
					add("-js")
					add("-sd")
					add(tmpDir.toAbsolutePath().toString())
				}
				addAll(listOf("-a", "127.0.0.1", "-p", port.toString()))
			}

		val builder = ProcessBuilder(command)
		builder.redirectErrorStream(true)
		return builder.start()
	}

	private fun consumeOutput(
		reader: BufferedReader,
		logFile: File,
	): Thread =
		thread(start = true, isDaemon = true, name = "nats-server-stdout") {
			try {
				BufferedWriter(FileWriter(logFile, true)).use { fileWriter ->
					reader.useLines { lines ->
						lines.forEach { line ->
							synchronized(outputLines) { outputLines += line }
							fileWriter.appendLine(line)
							fileWriter.flush()
						}
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

	companion object {
		@OptIn(ExperimentalContracts::class)
		inline fun runTest(crossinline block: suspend TestScope.(NatsServerHarness) -> Unit) {
			kotlinx.coroutines.test.runTest {
				block(NatsServerHarness())
			}
		}

		@OptIn(ExperimentalContracts::class)
		inline fun runBlocking(crossinline block: suspend CoroutineScope.(NatsServerHarness) -> Unit) {
			kotlinx.coroutines.runBlocking {
				withTimeout(20_000) {
					block(NatsServerHarness())
				}
			}
		}
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

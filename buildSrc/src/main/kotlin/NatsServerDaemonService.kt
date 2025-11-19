import org.gradle.api.GradleException
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.file.RegularFileProperty
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

abstract class NatsServerDaemonService :
	BuildService<NatsServerDaemonService.Params>,
	AutoCloseable {

	interface Params : BuildServiceParameters {
		val executable: RegularFileProperty
		val args: ListProperty<String>
		val readyCheckUrl: Property<String>
		val startupTimeoutSeconds: Property<Int>
		val environment: MapProperty<String, String>
	}

	private val logger = Logging.getLogger(NatsServerDaemonService::class.java)
	private val managedProcess = startOrReuse()

	override fun close() {
		if (!managedProcess.shouldTerminate) {
			logger.debug("NATS test harness daemon managed outside of Gradle – leaving it running")
			return
		}

		val process = managedProcess.process ?: return
		if (!process.isAlive) {
			return
		}

		logger.lifecycle("Stopping NATS test harness daemon")
		process.destroy()
		if (!process.waitFor(10, TimeUnit.SECONDS)) {
			logger.warn("NATS test harness daemon did not stop gracefully, forcing termination")
			process.destroyForcibly()
			process.waitFor(5, TimeUnit.SECONDS)
		}
	}

	private fun startOrReuse(): ManagedProcess {
		val healthUrl = parameters.readyCheckUrl.orNull
		if (healthUrl != null && isHealthy(healthUrl)) {
			logger.lifecycle("Reusing running NATS test harness daemon at $healthUrl")
			return ManagedProcess(null, shouldTerminate = false)
		}

		val executableFile = parameters.executable.asFile.orNull
			?: throw GradleException("NATS test harness executable was not configured")

		if (!executableFile.exists()) {
			throw GradleException(
				"NATS test harness executable missing at ${executableFile.absolutePath}. " +
					"Make sure :test-harness:nats-server-daemon:installDist ran successfully.",
			)
		}

		val command = buildCommand(executableFile)
		val processBuilder = ProcessBuilder(command)
			.directory(executableFile.parentFile)
			.redirectOutput(ProcessBuilder.Redirect.INHERIT)
			.redirectError(ProcessBuilder.Redirect.INHERIT)
		val env = parameters.environment.getOrElse(emptyMap())
		processBuilder.environment().putAll(env)

		logger.lifecycle("Starting NATS test harness daemon")
		val process = processBuilder.start()
		if (healthUrl != null) {
			waitUntilHealthy(process, healthUrl)
		}

		return ManagedProcess(process, shouldTerminate = true)
	}

	private fun buildCommand(executable: File): List<String> {
		val command = mutableListOf(executable.absolutePath)
		command.addAll(parameters.args.getOrElse(emptyList()))
		return command
	}

	private fun waitUntilHealthy(
		process: Process,
		healthUrl: String,
	) {
		val timeoutSeconds = parameters.startupTimeoutSeconds.orNull ?: 60
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds.toLong())

		while (System.nanoTime() < deadline) {
			if (!process.isAlive) {
				val exit = process.waitFor()
				throw GradleException("NATS test harness daemon exited early with status $exit")
			}
			if (isHealthy(healthUrl)) {
				logger.lifecycle("NATS test harness daemon is ready at $healthUrl")
				return
			}
			Thread.sleep(500)
		}

		process.destroy()
		throw GradleException("Timed out waiting for NATS test harness daemon at $healthUrl")
	}

	private fun isHealthy(url: String): Boolean {
		var connection: HttpURLConnection? = null
		return try {
			connection = URI(url).toURL().openConnection() as HttpURLConnection
			connection.connectTimeout = 1_000
			connection.readTimeout = 1_000
			connection.requestMethod = "GET"
			connection.inputStream.use { it.readBytes() }
			connection.responseCode in 200..299
		} catch (_: Exception) {
			false
		} finally {
			connection?.disconnect()
		}
	}

	private data class ManagedProcess(
		val process: Process?,
		val shouldTerminate: Boolean,
	)
}

@file:OptIn(ExperimentalUuidApi::class)

package harness

import kotlinx.coroutines.delay
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public class NatsServerHarness private constructor(
	private val enableJetStream: Boolean,
	private val enableTls: Boolean,
	private val tlsHandshakeFirst: Boolean,
	private val tlsRequireClientCert: Boolean,
	private val logId: String,
	fixedPort: Int?,
) : AutoCloseable {
	private val port: Int = fixedPort ?: ServerSocket(0).use { it.localPort }
	private val websocketPort: Int = ServerSocket(0).use { it.localPort }
	private val tmpDir = Files.createTempDirectory("nats") ?: Path.of("/tmp/nats-test/${Uuid.random()}")

	private var serverCertPem: String? = null
	private var clientCertPem: String? = null
	private var clientKeyPem: String? = null

	private val configFile = createConfigFile()
	private val logFile =
		Path
			.of("build/test-results/logs/nats/$logId.out")
			.also {
				Files.createDirectories(it.parent)
				Files.deleteIfExists(it)
				Files.createFile(it)
			}.toFile()

	private val process: Process = startProcess()
	private val outputLines = mutableListOf<String>()
	private val stdoutReader: Thread = consumeOutput(process.inputStream.bufferedReader(), logFile)

	public val uri: String
		get() = "nats://127.0.0.1:$port"

	// Connect via DNS rather than IP. iOS Simulator's SecTrust SSL policy is stricter about
	// IP-based hostnames in cert SANs and rejects what macOS accepts.
	public val tlsUri: String?
		get() = if (enableTls) "tls://localhost:$port" else null

	public val websocketUri: String
		get() = "ws://127.0.0.1:$websocketPort"

	public val logs: List<String>
		get() = synchronized(outputLines) { outputLines.toList() }

	/** PEM-encoded server leaf certificate. Non-null when [enableTls] is true. */
	public val serverCertificatePem: String?
		get() = serverCertPem

	/** PEM-encoded client cert signed by the harness's client CA. Non-null when [tlsRequireClientCert] is true. */
	public val clientCertificatePem: String?
		get() = clientCertPem

	/** PKCS#8 PEM-encoded private key for [clientCertificatePem]. */
	public val clientKeyPemPkcs8: String?
		get() = clientKeyPem

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
				addAll(listOf("-c", configFile.toAbsolutePath().toString()))
			}

		val builder = ProcessBuilder(command)
		builder.redirectErrorStream(true)
		return builder.start()
	}

	private fun createConfigFile(): Path {
		val tlsBlock =
			if (enableTls) {
				generateServerCert()
				val clientCaLine =
					if (tlsRequireClientCert) {
						val clientCaFile = generateClientCa()
						"\tca_file: \"${clientCaFile.toAbsolutePath()}\"\n\tverify: true\n"
					} else {
						""
					}
				val handshakeFirstLine = if (tlsHandshakeFirst) "\thandshake_first: true\n" else ""
				"""
				tls {
					cert_file: "${tmpDir.resolve("server-cert.pem").toAbsolutePath()}"
					key_file: "${tmpDir.resolve("server-key.pem").toAbsolutePath()}"
				$clientCaLine$handshakeFirstLine}
				""".trimIndent()
			} else {
				""
			}

		val config =
			"""
			jetstream {
				strict: true
			}

			websocket {
				no_tls: true
				same_origin: false
				port: $websocketPort
			}

			$tlsBlock
			""".trimIndent()

		return Files
			.createTempFile(tmpDir, "nats-server", ".conf")
			.also { path ->
				Files.writeString(path, config)
			}
	}

	private fun generateServerCert() {
		// Generate a CA and a server cert signed by it, rather than a self-signed leaf.
		// iOS's SecTrust SSL policy rejects self-signed-leaf-as-anchor configurations
		// with errSecPolicyDenied (-26276), even when the leaf is added via
		// SecTrustSetAnchorCertificates. A proper CA → leaf chain validates everywhere.
		val caKey = tmpDir.resolve("server-ca-key.pem")
		val caCert = tmpDir.resolve("server-ca-cert.pem")
		val keyFile = tmpDir.resolve("server-key.pem")
		val csrFile = tmpDir.resolve("server.csr")
		val certFile = tmpDir.resolve("server-cert.pem")
		val extFile = tmpDir.resolve("server-cert.ext")

		Files.writeString(
			extFile,
			"basicConstraints=critical,CA:FALSE\n" +
				"keyUsage=critical,digitalSignature,keyEncipherment,keyAgreement\n" +
				"subjectAltName=DNS:localhost,IP:127.0.0.1\n" +
				"extendedKeyUsage=serverAuth\n",
		)

		runOpenssl(
			"req",
			"-x509",
			"-newkey",
			"ec",
			"-pkeyopt",
			"ec_paramgen_curve:prime256v1",
			"-keyout",
			caKey.toAbsolutePath().toString(),
			"-out",
			caCert.toAbsolutePath().toString(),
			"-days",
			"1",
			"-nodes",
			"-subj",
			"/CN=natskt Test Server CA",
			// iOS's SecTrust policy requires a proper CA profile: explicit BasicConstraints CA:TRUE
			// and keyUsage with keyCertSign. Without these extensions, iOS rejects the anchor with
			// errSecPolicyDenied even though RFC 5280 permits CAs to omit keyUsage.
			"-addext",
			"basicConstraints=critical,CA:TRUE",
			"-addext",
			"keyUsage=critical,keyCertSign,cRLSign",
		)

		runOpenssl(
			"req",
			"-new",
			"-newkey",
			"ec",
			"-pkeyopt",
			"ec_paramgen_curve:prime256v1",
			"-keyout",
			keyFile.toAbsolutePath().toString(),
			"-out",
			csrFile.toAbsolutePath().toString(),
			"-nodes",
			"-subj",
			"/CN=localhost",
		)

		runOpenssl(
			"x509",
			"-req",
			"-in",
			csrFile.toAbsolutePath().toString(),
			"-CA",
			caCert.toAbsolutePath().toString(),
			"-CAkey",
			caKey.toAbsolutePath().toString(),
			"-CAcreateserial",
			"-out",
			certFile.toAbsolutePath().toString(),
			"-days",
			"1",
			"-extfile",
			extFile.toAbsolutePath().toString(),
		)

		// Expose the CA so tests can trust the chain by trusting the root.
		serverCertPem = Files.readString(caCert)
	}

	private fun generateClientCa(): Path {
		val caKey = tmpDir.resolve("client-ca-key.pem")
		val caCert = tmpDir.resolve("client-ca-cert.pem")
		val clientKey = tmpDir.resolve("client-key.pem")
		val clientKeyPkcs8 = tmpDir.resolve("client-key-pkcs8.pem")
		val clientCsr = tmpDir.resolve("client.csr")
		val clientCert = tmpDir.resolve("client-cert.pem")
		val extFile = tmpDir.resolve("client-cert.ext")

		Files.writeString(extFile, "extendedKeyUsage=clientAuth\n")

		// Use RSA for the client cert: Ktor's CIO TLS implementation rejects ECDSA
		// client certs during the handshake (see TLSClientHandshake.sendClientCertificate).
		runOpenssl(
			"req",
			"-x509",
			"-newkey",
			"rsa:2048",
			"-keyout",
			caKey.toAbsolutePath().toString(),
			"-out",
			caCert.toAbsolutePath().toString(),
			"-days",
			"1",
			"-nodes",
			"-subj",
			"/CN=Test Client CA",
		)

		runOpenssl(
			"req",
			"-new",
			"-newkey",
			"rsa:2048",
			"-keyout",
			clientKey.toAbsolutePath().toString(),
			"-out",
			clientCsr.toAbsolutePath().toString(),
			"-nodes",
			"-subj",
			"/CN=test-client",
		)

		runOpenssl(
			"x509",
			"-req",
			"-in",
			clientCsr.toAbsolutePath().toString(),
			"-CA",
			caCert.toAbsolutePath().toString(),
			"-CAkey",
			caKey.toAbsolutePath().toString(),
			"-CAcreateserial",
			"-out",
			clientCert.toAbsolutePath().toString(),
			"-days",
			"1",
			"-extfile",
			extFile.toAbsolutePath().toString(),
		)

		runOpenssl(
			"pkcs8",
			"-topk8",
			"-nocrypt",
			"-in",
			clientKey.toAbsolutePath().toString(),
			"-out",
			clientKeyPkcs8.toAbsolutePath().toString(),
		)

		clientCertPem = Files.readString(clientCert)
		clientKeyPem = Files.readString(clientKeyPkcs8)

		return caCert
	}

	private fun runOpenssl(vararg args: String) {
		val process =
			ProcessBuilder(listOf("openssl") + args)
				.redirectErrorStream(true)
				.start()
		val exitCode = process.waitFor()
		if (exitCode != 0) {
			val output = process.inputStream.bufferedReader().readText()
			throw IllegalStateException("openssl ${args.joinToString(" ")} failed (exit $exitCode): $output")
		}
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

	private suspend fun waitForReady() {
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
		var tcpReady = false
		var wsReady = false
		while (System.nanoTime() < deadline) {
			if (!process.isAlive) {
				throw IllegalStateException("nats-server exited early with ${process.exitValue()}")
			}

			tcpReady = tcpReady || probePort(port)
			wsReady = wsReady || probePort(websocketPort)

			if (tcpReady && wsReady) {
				delay(200)
				return
			}

			delay(50)
		}
		throw IllegalStateException("Timed out waiting for nats-server to start")
	}

	private fun probePort(port: Int): Boolean =
		try {
			Socket().use { socket ->
				socket.connect(InetSocketAddress("127.0.0.1", port), 200)
			}
			true
		} catch (_: Exception) {
			false
		}

	override fun close() {
		process.destroy()
		if (!process.waitFor(3, TimeUnit.SECONDS)) {
			process.destroyForcibly()
			process.waitFor(3, TimeUnit.SECONDS)
		}
		stdoutReader.join(500)
	}

	public companion object {
		public suspend operator fun invoke(
			enableJetStream: Boolean = true,
			enableTls: Boolean = false,
			tlsHandshakeFirst: Boolean = false,
			tlsRequireClientCert: Boolean = false,
			logId: String,
			fixedPort: Int? = null,
		): NatsServerHarness {
			val harness =
				NatsServerHarness(
					enableJetStream = enableJetStream,
					enableTls = enableTls,
					tlsHandshakeFirst = tlsHandshakeFirst,
					tlsRequireClientCert = tlsRequireClientCert,
					logId = logId,
					fixedPort = fixedPort,
				)
			harness.waitForReady()
			return harness
		}
	}
}

package io.natskt.harness.tls

import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File

fun main() {
	val host = System.getenv("TLS_TEST_SERVER_HOST") ?: "127.0.0.1"
	val httpPort = (System.getenv("TLS_TEST_SERVER_PORT") ?: "4501").toInt()

	println("TLS Test Server starting...")

	// Generate self-signed certificates
	val ecCert = CertificateGenerator.generateEc(cn = "localhost", sans = listOf("localhost", "127.0.0.1"))
	val rsaCert = CertificateGenerator.generateRsa(cn = "localhost", sans = listOf("localhost", "127.0.0.1"))

	// Start TLS echo endpoints
	val tlsEndpoints = TlsEndpoints(ecCert.sslContext, rsaCert.sslContext)
	val tlsPorts = tlsEndpoints.startAll()
	println("TLS endpoints started: $tlsPorts")

	// Start raw TCP error endpoints
	val rawEndpoints = RawEndpoints()
	val rawPorts = rawEndpoints.startAll()
	println("Raw endpoints started: $rawPorts")

	// Start ClientHello inspector
	val inspector = ClientHelloInspector()
	val inspectorPort = inspector.start()
	println("ClientHello inspector started on port $inspectorPort")

	// Combine all ports
	val allPorts = mutableMapOf<String, Int>()
	allPorts.putAll(tlsPorts)
	allPorts.putAll(rawPorts)
	allPorts["client_hello_inspector"] = inspectorPort

	// Write ports file
	val portsFile = File("ports.properties")
	portsFile.writeText(
		allPorts.entries
			.sortedBy { it.key }
			.joinToString("\n") { "${it.key}=${it.value}" } + "\n",
	)
	println("Ports written to ${portsFile.absolutePath}")

	// Register shutdown hook
	Runtime.getRuntime().addShutdownHook(
		Thread {
			println("Shutting down TLS test server...")
			tlsEndpoints.stopAll()
			rawEndpoints.stopAll()
			inspector.stop()
		},
	)

	// Start HTTP control server (health check + ClientHello query)
	embeddedServer(Netty, port = httpPort, host = host) {
		routing {
			get("/health") {
				call.respondText("ok")
			}
			get("/client-hello") {
				val parsed = inspector.lastClientHello.get()
				if (parsed == null) {
					call.respondText("no ClientHello captured yet", status = HttpStatusCode.NotFound)
					return@get
				}
				val text =
					buildString {
						appendLine("LEGACY_VERSION=0x${parsed.legacyVersion.toString(16).padStart(4, '0')}")
						appendLine("RANDOM_LENGTH=${parsed.randomLength}")
						appendLine("SESSION_ID_LENGTH=${parsed.sessionIdLength}")
						appendLine("CIPHER_SUITES=${parsed.cipherSuites.joinToString(",") { "0x${it.toString(16).padStart(4, '0')}" }}")
						appendLine("COMPRESSION_METHODS=${parsed.compressionMethods.joinToString(",") { "0x${it.toString(16).padStart(2, '0')}" }}")
						parsed.extensions[0x0000]?.let { data ->
							ClientHelloInspector.parseSniExtension(data)?.let { appendLine("EXT_SNI=$it") }
						}
						parsed.extensions[0x002b]?.let { data ->
							val versions = ClientHelloInspector.parseSupportedVersions(data)
							appendLine("EXT_SUPPORTED_VERSIONS=${versions.joinToString(",") { "0x${it.toString(16).padStart(4, '0')}" }}")
						}
						parsed.extensions[0x0033]?.let { data ->
							val groups = ClientHelloInspector.parseKeyShareGroups(data)
							appendLine("EXT_KEY_SHARE_GROUPS=${groups.joinToString(",") { "0x${it.toString(16).padStart(4, '0')}" }}")
						}
						parsed.extensions[0x000d]?.let { data ->
							val algs = ClientHelloInspector.parseSignatureAlgorithms(data)
							appendLine("EXT_SIGNATURE_ALGORITHMS=${algs.joinToString(",") { "0x${it.toString(16).padStart(4, '0')}" }}")
						}
						parsed.extensions[0x000a]?.let { data ->
							val groups = ClientHelloInspector.parseSupportedGroups(data)
							appendLine("EXT_SUPPORTED_GROUPS=${groups.joinToString(",") { "0x${it.toString(16).padStart(4, '0')}" }}")
						}
						appendLine("EXT_TYPES_PRESENT=${parsed.extensions.keys.joinToString(",") { "0x${it.toString(16).padStart(4, '0')}" }}")
					}
				call.respondText(text)
			}
		}
	}.start(wait = true)
}

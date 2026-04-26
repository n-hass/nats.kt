@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.natskt.tls

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.pin
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.getenv

/**
 * Reads TLS test server port assignments from the properties file
 * written by the JVM TLS test server at startup.
 */
object TlsTestPorts {
	private val ports: Map<String, Int> by lazy { loadPorts() }

	val tlsDefault: Int get() = ports.getValue("tls_default")
	val tls13Only: Int get() = ports.getValue("tls13_only")
	val tls12Only: Int get() = ports.getValue("tls12_only")
	val tls13Aes128: Int get() = ports.getValue("tls13_aes128")
	val tls13Aes256: Int get() = ports.getValue("tls13_aes256")
	val tls13Chacha: Int get() = ports.getValue("tls13_chacha")
	val sniCapture: Int get() = ports.getValue("sni_capture")
	val tls13HrrP384: Int get() = ports.getValue("tls13_hrr_p384")
	val tls12EcdheEcdsa: Int get() = ports.getValue("tls12_ecdhe_ecdsa")
	val tls12EcdheRsa: Int get() = ports.getValue("tls12_ecdhe_rsa")
	val serverClose: Int get() = ports.getValue("server_close")
	val clientHelloInspector: Int get() = ports.getValue("client_hello_inspector")
	val fatalAlert: Int get() = ports.getValue("fatal_alert")
	val immediateClose: Int get() = ports.getValue("immediate_close")
	val garbage: Int get() = ports.getValue("garbage")
	val truncatedRecord: Int get() = ports.getValue("truncated_record")
	val oversizedRecord: Int get() = ports.getValue("oversized_record")

	private fun loadPorts(): Map<String, Int> {
		val filePath =
			getenv("TLS_TEST_PORTS_FILE")?.toKString()
				?: error(
					"TLS_TEST_PORTS_FILE environment variable not set. " +
						"Ensure the TLS test server is running via Gradle.",
				)

		val content = readFileContents(filePath)
		return content
			.lines()
			.map { it.trim() }
			.filter { it.isNotEmpty() && '=' in it && !it.startsWith('#') }
			.associate { line ->
				val (key, value) = line.split("=", limit = 2)
				key.trim() to value.trim().toInt()
			}
	}
}

/**
 * Read entire file contents as a string using POSIX APIs.
 * Works on all native platforms (macOS, iOS, Linux).
 */
private fun readFileContents(path: String): String {
	val file =
		fopen(path, "r")
			?: error("Cannot open file: $path")
	try {
		val chunks = mutableListOf<String>()
		val buf = ByteArray(4096)
		while (true) {
			val pinned = buf.pin()
			val read =
				try {
					fread(pinned.addressOf(0), 1u, buf.size.toULong(), file)
				} finally {
					pinned.unpin()
				}
			if (read == 0uL) break
			chunks.add(buf.decodeToString(0, read.toInt()))
		}
		return chunks.joinToString("")
	} finally {
		fclose(file)
	}
}

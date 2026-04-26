package io.natskt.tls

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Run a TLS test with real I/O dispatchers (not virtual time).
 * Follows the same pattern as RemoteNatsHarness.runBlocking.
 *
 * A local TLS handshake + echo should complete well under 5 seconds.
 */
fun tlsTest(
	timeout: Duration = 5.seconds,
	block: suspend CoroutineScope.() -> Unit,
): TestResult =
	runTest(timeout = timeout) {
		withContext(Dispatchers.Default) {
			block()
		}
	}

/**
 * Connect to the TLS test server on the given port, perform a TLS handshake,
 * and return the [NativeTlsConnection].
 */
suspend fun connectTls(
	port: Int,
	serverName: String? = "localhost",
	verifyCertificates: Boolean = false,
	timeout: Duration = 3.seconds,
): NativeTlsConnection =
	withTimeout(timeout) {
		val selector = SelectorManager(Dispatchers.IO)
		val socket =
			aSocket(selector)
				.tcp()
				.connect("127.0.0.1", port)
		val conn = socket.connection()
		conn.nativeTls(Dispatchers.IO) {
			this.serverName = serverName
			this.verifyCertificates = verifyCertificates
		}
	}

/**
 * Connect to the TLS test server, send data, read the echo, and verify it matches.
 * Closes the connection after verification.
 */
suspend fun assertTlsEcho(
	port: Int,
	message: String = "hello",
	serverName: String? = "localhost",
) {
	val tls = connectTls(port, serverName)
	try {
		val data = message.encodeToByteArray()
		tls.output.writeFully(data)
		tls.output.flush()

		val echo = ByteArray(data.size)
		tls.input.readFully(echo)
		assertEquals(message, echo.decodeToString(), "Echo mismatch on port $port")
	} finally {
		tls.close()
	}
}

/**
 * Connect to the TLS test server, send binary data, read the echo, and verify it matches.
 */
suspend fun assertTlsBinaryEcho(
	port: Int,
	data: ByteArray,
	serverName: String? = "localhost",
) {
	val tls = connectTls(port, serverName)
	try {
		tls.output.writeFully(data)
		tls.output.flush()

		val echo = ByteArray(data.size)
		tls.input.readFully(echo)
		assertEquals(data.toList(), echo.toList(), "Binary echo mismatch on port $port")
	} finally {
		tls.close()
	}
}

/**
 * Parse the key=value text format sent by the ClientHello inspector endpoint.
 * Returns a map of field names to values.
 */
fun parseInspectorResponse(text: String): Map<String, String> =
	text
		.lines()
		.map { it.trim() }
		.filter { it.isNotEmpty() && it != "---" && '=' in it }
		.associate { line ->
			val (key, value) = line.split("=", limit = 2)
			key.trim() to value.trim()
		}

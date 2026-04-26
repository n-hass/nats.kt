package io.natskt.tls

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 6066 §3 — Server Name Indication (SNI) extension tests.
 *
 * Verifies that the TLS client correctly sends (or omits) the SNI extension
 * based on the configured serverName.
 */
class SniExtensionTest {
	/**
	 * RFC 6066 §3: A client that supports SNI SHOULD include the server_name extension
	 * in the ClientHello when a hostname is available.
	 */
	@Test
	fun `sends SNI when serverName is configured`() =
		tlsTest {
			val tls =
				connectTls(
					port = TlsTestPorts.sniCapture,
					serverName = "localhost",
				)
			try {
				// The SNI capture endpoint sends "SNI=hostname\n" after handshake
				val sniLine = tls.input.readLine()
				assertNotNull(sniLine, "Expected SNI response from server")
				assertTrue(sniLine.startsWith("SNI="), "Expected SNI= prefix, got: $sniLine")
				val sniValue = sniLine.substringAfter("SNI=")
				assertTrue(sniValue.isNotEmpty(), "SNI hostname should not be empty when serverName is set")
			} finally {
				tls.close()
			}
		}

	/**
	 * RFC 6066 §3: The hostname in the SNI extension MUST match the server name
	 * the client is attempting to connect to.
	 */
	@Test
	fun `SNI contains correct hostname`() =
		tlsTest {
			val tls =
				connectTls(
					port = TlsTestPorts.sniCapture,
					serverName = "localhost",
				)
			try {
				val sniLine = tls.input.readLine()
				assertNotNull(sniLine)
				val sniValue = sniLine.substringAfter("SNI=")
				assertEquals("localhost", sniValue, "SNI hostname must match configured serverName")
			} finally {
				tls.close()
			}
		}

	/**
	 * When serverName is null, the client should not include the SNI extension.
	 * Verified by connecting to the ClientHello inspector with null serverName,
	 * then querying the HTTP API for the parsed ClientHello.
	 */
	@Test
	fun `omits SNI when serverName is null`() =
		tlsTest {
			// Connect to inspector with null serverName — connection will fail (inspector closes)
			runCatching {
				connectTls(
					port = TlsTestPorts.clientHelloInspector,
					serverName = null,
				)
			}

			// Query the HTTP API for the parsed ClientHello
			val selector = SelectorManager(Dispatchers.IO)
			val socket = aSocket(selector).tcp().connect("127.0.0.1", 4501)
			val conn = socket.connection()
			val request = "GET /client-hello HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n"
			conn.output.writeFully(request.encodeToByteArray())
			conn.output.flush()

			val lines = mutableListOf<String>()
			var inBody = false
			while (true) {
				val line = conn.input.readLine() ?: break
				if (inBody) {
					lines.add(line)
				} else if (line.isBlank()) {
					inBody = true
				}
			}
			socket.close()

			val response = parseInspectorResponse(lines.joinToString("\n"))

			// EXT_SNI should not be present when serverName is null
			assertFalse(
				response.containsKey("EXT_SNI"),
				"SNI extension should not be present when serverName is null",
			)

			// Also verify via extension type: 0x0000 (SNI) should not be in the list
			val extTypes = response["EXT_TYPES_PRESENT"] ?: ""
			assertFalse(
				extTypes.split(",").contains("0x0000"),
				"SNI extension type (0x0000) should not be in extensions when serverName is null",
			)
		}

	/**
	 * Verify the SNI capture endpoint still functions as an echo server after
	 * reporting the SNI hostname, confirming the handshake completed correctly.
	 */
	@Test
	fun `SNI capture endpoint echoes data after reporting SNI`() =
		tlsTest {
			val tls =
				connectTls(
					port = TlsTestPorts.sniCapture,
					serverName = "localhost",
				)
			try {
				// Read and discard the SNI line
				tls.input.readLine()

				// Verify echo works
				val data = "sni-echo-test".encodeToByteArray()
				tls.output.writeFully(data)
				tls.output.flush()

				val echo = ByteArray(data.size)
				tls.input.readFully(echo)
				assertEquals("sni-echo-test", echo.decodeToString())
			} finally {
				tls.close()
			}
		}
}

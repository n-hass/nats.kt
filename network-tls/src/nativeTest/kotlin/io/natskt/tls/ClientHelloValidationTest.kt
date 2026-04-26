package io.natskt.tls

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.connection
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RFC 8446 §4.1.2 — ClientHello validation.
 *
 * Each test triggers a TLS connection to the ClientHello inspector endpoint (which
 * captures the raw ClientHello and closes the connection), then queries the HTTP
 * control API for the parsed fields.
 */
class ClientHelloValidationTest {
	/**
	 * Trigger a ClientHello capture by connecting to the inspector,
	 * then query the HTTP API for the parsed result.
	 */
	private suspend fun captureClientHello(serverName: String? = "localhost"): Map<String, String> {
		// Connect to inspector — it captures the ClientHello then closes the connection
		runCatching {
			connectTls(
				port = TlsTestPorts.clientHelloInspector,
				serverName = serverName,
			)
		}
		// The connection is expected to fail since the inspector doesn't complete the handshake.
		// Now query the HTTP API for the parsed ClientHello.
		return queryClientHello()
	}

	/**
	 * Simple HTTP GET to the TLS test server's /client-hello endpoint.
	 * Uses raw TCP via Ktor sockets — no HTTP client library needed.
	 */
	private suspend fun queryClientHello(): Map<String, String> {
		val selector = SelectorManager(Dispatchers.IO)
		val socket = aSocket(selector).tcp().connect("127.0.0.1", 4501)
		val conn = socket.connection()
		val request = "GET /client-hello HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n"
		conn.output.writeFully(request.encodeToByteArray())
		conn.output.flush()

		// Read response: skip HTTP headers (lines until empty line), then parse body
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

		return parseInspectorResponse(lines.joinToString("\n"))
	}

	@Test
	fun `clientHello legacy_version is 0x0303`() =
		tlsTest {
			val hello = captureClientHello()
			assertEquals("0x0303", hello["LEGACY_VERSION"], "legacy_version must be 0x0303 per RFC 8446 §4.1.2")
		}

	@Test
	fun `clientHello random is 32 bytes`() =
		tlsTest {
			val hello = captureClientHello()
			assertEquals("32", hello["RANDOM_LENGTH"], "random must be 32 bytes per RFC 8446 §4.1.2")
		}

	@Test
	fun `clientHello session_id is non-empty for middlebox compatibility`() =
		tlsTest {
			val hello = captureClientHello()
			val sessionIdLength = hello["SESSION_ID_LENGTH"]?.toIntOrNull()
			assertNotNull(sessionIdLength, "SESSION_ID_LENGTH must be present")
			assertTrue(sessionIdLength > 0, "session_id must be non-empty for middlebox compatibility (RFC 8446 Appendix D.4)")
		}

	@Test
	fun `clientHello offers TLS_AES_128_GCM_SHA256`() =
		tlsTest {
			val hello = captureClientHello()
			val suites = hello["CIPHER_SUITES"] ?: ""
			assertContains(suites, "0x1301", message = "Must offer TLS_AES_128_GCM_SHA256 per RFC 8446 §9.2")
		}

	@Test
	fun `clientHello compression_methods is only null`() =
		tlsTest {
			val hello = captureClientHello()
			assertEquals("0x00", hello["COMPRESSION_METHODS"], "compression_methods must be [0x00] per RFC 8446 §4.1.2")
		}

	@Test
	fun `clientHello includes supported_versions with 0x0304`() =
		tlsTest {
			val hello = captureClientHello()
			val versions = hello["EXT_SUPPORTED_VERSIONS"] ?: ""
			assertContains(versions, "0x0304", message = "supported_versions must include TLS 1.3 (0x0304)")
		}

	@Test
	fun `clientHello includes key_share extension`() =
		tlsTest {
			val hello = captureClientHello()
			val extTypes = hello["EXT_TYPES_PRESENT"] ?: ""
			assertContains(extTypes, "0x0033", message = "key_share extension (0x0033) must be present per RFC 8446 §4.2.8")
		}

	@Test
	fun `clientHello includes signature_algorithms extension`() =
		tlsTest {
			val hello = captureClientHello()
			val extTypes = hello["EXT_TYPES_PRESENT"] ?: ""
			assertContains(extTypes, "0x000d", message = "signature_algorithms extension (0x000d) must be present per RFC 8446 §4.2.3")
		}

	@Test
	fun `clientHello includes supported_groups extension`() =
		tlsTest {
			val hello = captureClientHello()
			val extTypes = hello["EXT_TYPES_PRESENT"] ?: ""
			assertContains(extTypes, "0x000a", message = "supported_groups extension (0x000a) must be present per RFC 8446 §4.2.7")
		}

	@Test
	fun `clientHello key_share has at least one entry`() =
		tlsTest {
			val hello = captureClientHello()
			val groups = hello["EXT_KEY_SHARE_GROUPS"] ?: ""
			assertTrue(groups.isNotEmpty(), "key_share must contain at least one group per RFC 8446 §4.2.8")
		}
}

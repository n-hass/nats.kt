package io.natskt.tls

import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.async
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RFC 8446 §5 — Application data exchange tests.
 *
 * Verifies that after a TLS handshake, data of various sizes and patterns
 * can be exchanged correctly over the encrypted connection. Tests exercise
 * the record layer's ability to handle fragmentation, multiple records,
 * and concurrent I/O.
 */
class ApplicationDataTest {
	/**
	 * Basic data integrity: a small text message echoes back correctly.
	 */
	@Test
	fun `echoes small message correctly`() =
		tlsTest {
			assertTlsEcho(TlsTestPorts.tls13Only, message = "hello, TLS!")
		}

	/**
	 * RFC 8446 §5.1: The record layer fragments data into records of at most 2^14 (16384) bytes.
	 * Sending a 50KB payload forces multiple TLS records. The implementation must correctly
	 * fragment on send and reassemble on receive.
	 */
	@Test
	fun `echoes large payload spanning multiple records`() =
		tlsTest {
			val payload = ByteArray(50_000) { (it % 256).toByte() }
			assertTlsBinaryEcho(TlsTestPorts.tls13Only, payload)
		}

	/**
	 * Verify multiple sequential messages each echo correctly without cross-contamination.
	 * This tests that the record layer maintains correct state across messages.
	 */
	@Test
	fun `handles multiple sequential messages`() =
		tlsTest {
			val tls = connectTls(TlsTestPorts.tls13Only)
			try {
				val messages =
					listOf(
						"first message",
						"second message with different length",
						"3",
						"the quick brown fox jumps over the lazy dog",
					)

				for (msg in messages) {
					val data = msg.encodeToByteArray()
					tls.output.writeFully(data)
					tls.output.flush()

					val echo = ByteArray(data.size)
					tls.input.readFully(echo)
					assertEquals(msg, echo.decodeToString(), "Sequential message echo mismatch")
				}
			} finally {
				tls.close()
			}
		}

	/**
	 * Test concurrent read and write operations. The client writes data while
	 * simultaneously reading the echo response. This exercises the bidirectional
	 * nature of the TLS record layer.
	 */
	@Test
	fun `handles concurrent read and write`() =
		tlsTest {
			val tls = connectTls(TlsTestPorts.tls13Only)
			try {
				val data = ByteArray(8192) { (it % 251).toByte() }

				// Write in a separate coroutine
				val writeJob =
					async {
						tls.output.writeFully(data)
						tls.output.flush()
					}

				// Read concurrently
				val echo = ByteArray(data.size)
				tls.input.readFully(echo)

				writeJob.await()
				assertEquals(data.toList(), echo.toList(), "Concurrent read/write echo mismatch")
			} finally {
				tls.close()
			}
		}

	/**
	 * Verify binary data with all byte values (0x00-0xFF) is transmitted correctly.
	 * Ensures the TLS layer does not corrupt any byte patterns.
	 */
	@Test
	fun `echoes binary data correctly`() =
		tlsTest {
			val payload = ByteArray(256) { it.toByte() } // All byte values 0x00..0xFF
			assertTlsBinaryEcho(TlsTestPorts.tls13Only, payload)
		}

	/**
	 * Verify data exchange also works over TLS 1.2 with a large payload.
	 * This ensures the TLS 1.2 record layer handles fragmentation correctly too.
	 */
	@Test
	fun `echoes large payload over TLS 1_2`() =
		tlsTest {
			val payload = ByteArray(50_000) { (it % 256).toByte() }
			assertTlsBinaryEcho(TlsTestPorts.tls12Only, payload)
		}
}

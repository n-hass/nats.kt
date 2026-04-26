package io.natskt.tls

import io.ktor.utils.io.readFully
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeFully
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * RFC 8446 §6.1 — Connection lifecycle and close notification tests.
 *
 * Verifies graceful connection teardown and that connections remain usable
 * across multiple operations after the handshake completes.
 */
class ConnectionLifecycleTest {
	/**
	 * RFC 8446 §6.1: Each party MUST send a close_notify alert before closing
	 * the write side of the connection. The close() method should complete without error.
	 */
	@Test
	fun `close sends close_notify and terminates`() =
		tlsTest {
			val tls = connectTls(TlsTestPorts.tlsDefault)
			// Exchange some data first to ensure the connection is fully established
			val data = "close-test".encodeToByteArray()
			tls.output.writeFully(data)
			tls.output.flush()
			val echo = ByteArray(data.size)
			tls.input.readFully(echo)
			assertEquals("close-test", echo.decodeToString())

			// Graceful close should not throw
			tls.close()
		}

	/**
	 * A TLS connection should remain usable for multiple read/write cycles
	 * after the handshake. This tests that internal state (sequence numbers,
	 * cipher state, etc.) is maintained correctly across operations.
	 */
	@Test
	fun `connection usable for multiple operations`() =
		tlsTest {
			val tls = connectTls(TlsTestPorts.tlsDefault)
			try {
				// Perform many echo operations on the same connection
				for (i in 1..20) {
					val msg = "operation-$i"
					val data = msg.encodeToByteArray()
					tls.output.writeFully(data)
					tls.output.flush()

					val echo = ByteArray(data.size)
					tls.input.readFully(echo)
					assertEquals(msg, echo.decodeToString(), "Echo mismatch on operation $i")
				}
			} finally {
				tls.close()
			}
		}

	/**
	 * After exchanging data and closing, opening a new connection to the same
	 * endpoint should work. This verifies the client can be used repeatedly.
	 */
	@Test
	fun `can open new connection after closing previous one`() =
		tlsTest {
			// First connection
			val tls1 = connectTls(TlsTestPorts.tlsDefault)
			val data1 = "conn1".encodeToByteArray()
			tls1.output.writeFully(data1)
			tls1.output.flush()
			val echo1 = ByteArray(data1.size)
			tls1.input.readFully(echo1)
			assertEquals("conn1", echo1.decodeToString())
			tls1.close()

			// Second connection
			val tls2 = connectTls(TlsTestPorts.tlsDefault)
			val data2 = "conn2".encodeToByteArray()
			tls2.output.writeFully(data2)
			tls2.output.flush()
			val echo2 = ByteArray(data2.size)
			tls2.input.readFully(echo2)
			assertEquals("conn2", echo2.decodeToString())
			tls2.close()
		}

	/**
	 * RFC 8446 §6.1: The server sends close_notify to indicate it will not send
	 * any more data. The client should receive all data sent before the close_notify
	 * and then observe the stream end without error.
	 */
	@Test
	fun `handles server-initiated close_notify gracefully`() =
		tlsTest {
			val tls = connectTls(TlsTestPorts.serverClose)
			// The server sends "goodbye\n" then closes.
			val line = tls.input.readLine()
			assertEquals("goodbye", line)
			// After the server closes, further reads should return null (EOF), not throw.
			val afterClose = tls.input.readLine()
			assertNull(afterClose, "Expected null after server close_notify, got: $afterClose")
			tls.close()
		}
}

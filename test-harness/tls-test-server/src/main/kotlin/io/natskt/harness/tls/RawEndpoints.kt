package io.natskt.harness.tls

import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Raw TCP endpoints that send crafted byte sequences to test TLS client error handling.
 * These are NOT TLS servers — they deliberately send bad/unexpected data.
 */
class RawEndpoints {
	data class EndpointInfo(
		val name: String,
		val port: Int,
		val thread: Thread,
	)

	private val endpoints = ConcurrentHashMap<String, EndpointInfo>()

	fun startAll(): Map<String, Int> {
		val ports = mutableMapOf<String, Int>()

		// Send a TLS fatal alert record: handshake_failure (0x28)
		// Record: ContentType=Alert(0x15), Version=TLS1.2(0x0303), Length=2, Level=Fatal(0x02), Type=handshake_failure(0x28)
		ports["fatal_alert"] =
			startRawEndpoint("fatal_alert") { output ->
				output.write(byteArrayOf(0x15, 0x03, 0x03, 0x00, 0x02, 0x02, 0x28))
				output.flush()
				// Keep connection open briefly so client can read the alert
				Thread.sleep(500)
			}

		// Accept connection then close immediately — simulates server crash during handshake
		ports["immediate_close"] =
			startRawEndpoint("immediate_close") { _ ->
				// Do nothing — socket closes when lambda returns
			}

		// Send random non-TLS garbage bytes
		ports["garbage"] =
			startRawEndpoint("garbage") { output ->
				val garbage = ByteArray(100) { (it * 37 + 13).toByte() } // deterministic "random"
				output.write(garbage)
				output.flush()
				Thread.sleep(500)
			}

		// Send a partial TLS record header (only 3 of 5 bytes) then close
		ports["truncated_record"] =
			startRawEndpoint("truncated_record") { output ->
				// Start of a Handshake record header but truncated
				output.write(byteArrayOf(0x16, 0x03, 0x03))
				output.flush()
				Thread.sleep(200)
			}

		// Send a TLS record with length exceeding the maximum (>16384 + 256 = 16640)
		// RFC 8446 §5.1: implementations MUST NOT send records exceeding 2^14+256
		ports["oversized_record"] =
			startRawEndpoint("oversized_record") { output ->
				// Handshake record with length = 0x5000 (20480 bytes) — exceeds limit
				val header = byteArrayOf(0x16, 0x03, 0x03, 0x50, 0x00)
				output.write(header)
				// Write the oversized payload
				val payload = ByteArray(0x5000)
				output.write(payload)
				output.flush()
				Thread.sleep(500)
			}

		return ports
	}

	fun stopAll() {
		endpoints.values.forEach { it.thread.interrupt() }
	}

	private fun startRawEndpoint(
		name: String,
		handler: (java.io.OutputStream) -> Unit,
	): Int {
		val serverSocket = ServerSocket(0)

		val thread =
			Thread({
				while (!Thread.currentThread().isInterrupted) {
					try {
						val socket = serverSocket.accept()
						Thread({
							try {
								socket.use { s ->
									handler(s.outputStream)
								}
							} catch (e: Exception) {
								System.err.println("[raw-$name] handler error: ${e.message}")
							}
						}, "raw-conn-$name-${socket.port}").apply {
							isDaemon = true
							start()
						}
					} catch (_: Exception) {
						break
					}
				}
			}, "raw-endpoint-$name").apply {
				isDaemon = true
				start()
			}

		endpoints[name] = EndpointInfo(name, serverSocket.localPort, thread)
		return serverSocket.localPort
	}
}

package io.natskt.harness.tls

import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference

/**
 * A raw TCP endpoint that captures and parses the ClientHello from connecting clients.
 *
 * This does NOT complete the TLS handshake — it reads the first TLS record (which
 * contains the ClientHello), parses it, stores the result, and closes the connection.
 * No SSLEngine or custom TLS protocol handling is involved.
 *
 * The parsed ClientHello is available via [lastClientHello] and can be queried
 * by tests after they attempt (and expectedly fail) a connection to this endpoint.
 */
class ClientHelloInspector {
	data class ParsedClientHello(
		val legacyVersion: Int,
		val randomLength: Int,
		val sessionIdLength: Int,
		val cipherSuites: List<Int>,
		val compressionMethods: List<Int>,
		val extensions: Map<Int, ByteArray>,
	)

	/** The most recently captured ClientHello. */
	val lastClientHello = AtomicReference<ParsedClientHello?>(null)

	private var thread: Thread? = null
	private var serverSocket: ServerSocket? = null

	fun start(): Int {
		val ss = ServerSocket(0)
		serverSocket = ss

		thread =
			Thread({
				while (!Thread.currentThread().isInterrupted) {
					try {
						val socket = ss.accept()
						Thread({
							try {
								socket.use { s ->
									captureClientHello(s.inputStream)
								}
							} catch (e: Exception) {
								System.err.println("[client-hello-inspector] error: ${e.message}")
							}
						}, "inspector-conn-${socket.port}").apply {
							isDaemon = true
							start()
						}
					} catch (_: Exception) {
						break
					}
				}
			}, "client-hello-inspector").apply {
				isDaemon = true
				start()
			}

		return ss.localPort
	}

	fun stop() {
		thread?.interrupt()
		serverSocket?.close()
	}

	private fun captureClientHello(rawIn: InputStream) {
		// Read the TLS record header (5 bytes): type(1) + version(2) + length(2)
		val recordHeader = rawIn.readNBytes(5)
		if (recordHeader.size < 5) return

		val recordLength = ((recordHeader[3].toInt() and 0xff) shl 8) or (recordHeader[4].toInt() and 0xff)
		val recordPayload = rawIn.readNBytes(recordLength)
		if (recordPayload.size < recordLength) return

		// Parse and store the ClientHello
		lastClientHello.set(parseClientHello(recordPayload))

		// Don't attempt to complete the handshake — just close the connection.
		// The client will receive a connection reset, which is expected.
	}

	companion object {
		fun parseClientHello(handshakePayload: ByteArray): ParsedClientHello {
			var pos = 0

			// Handshake header: type(1) + length(3)
			pos += 1 // skip type (0x01 = ClientHello)
			pos += 3 // skip length

			// ClientHello body
			val legacyVersion = ((handshakePayload[pos].toInt() and 0xff) shl 8) or (handshakePayload[pos + 1].toInt() and 0xff)
			pos += 2

			// random (32 bytes)
			val randomLength = 32
			pos += randomLength

			// session_id
			val sessionIdLength = handshakePayload[pos].toInt() and 0xff
			pos += 1 + sessionIdLength

			// cipher_suites
			val cipherSuitesLength = ((handshakePayload[pos].toInt() and 0xff) shl 8) or (handshakePayload[pos + 1].toInt() and 0xff)
			pos += 2
			val cipherSuites = mutableListOf<Int>()
			val csEnd = pos + cipherSuitesLength
			while (pos < csEnd) {
				val suite = ((handshakePayload[pos].toInt() and 0xff) shl 8) or (handshakePayload[pos + 1].toInt() and 0xff)
				cipherSuites.add(suite)
				pos += 2
			}

			// compression_methods
			val compMethodsLength = handshakePayload[pos].toInt() and 0xff
			pos += 1
			val compressionMethods = mutableListOf<Int>()
			val cmEnd = pos + compMethodsLength
			while (pos < cmEnd) {
				compressionMethods.add(handshakePayload[pos].toInt() and 0xff)
				pos += 1
			}

			// extensions
			val extensions = mutableMapOf<Int, ByteArray>()
			if (pos < handshakePayload.size - 1) {
				val extensionsLength = ((handshakePayload[pos].toInt() and 0xff) shl 8) or (handshakePayload[pos + 1].toInt() and 0xff)
				pos += 2
				val extEnd = pos + extensionsLength
				while (pos + 4 <= extEnd && pos + 4 <= handshakePayload.size) {
					val extType = ((handshakePayload[pos].toInt() and 0xff) shl 8) or (handshakePayload[pos + 1].toInt() and 0xff)
					pos += 2
					val extLen = ((handshakePayload[pos].toInt() and 0xff) shl 8) or (handshakePayload[pos + 1].toInt() and 0xff)
					pos += 2
					if (pos + extLen <= handshakePayload.size) {
						extensions[extType] = handshakePayload.copyOfRange(pos, pos + extLen)
					}
					pos += extLen
				}
			}

			return ParsedClientHello(
				legacyVersion = legacyVersion,
				randomLength = randomLength,
				sessionIdLength = sessionIdLength,
				cipherSuites = cipherSuites,
				compressionMethods = compressionMethods,
				extensions = extensions,
			)
		}

		/** Parse SNI extension (type 0x0000). */
		fun parseSniExtension(data: ByteArray): String? {
			if (data.size < 5) return null
			var pos = 0
			pos += 2 // server_name_list length
			val nameType = data[pos].toInt() and 0xff
			pos += 1
			if (nameType != 0) return null
			val nameLen = ((data[pos].toInt() and 0xff) shl 8) or (data[pos + 1].toInt() and 0xff)
			pos += 2
			if (pos + nameLen > data.size) return null
			return String(data, pos, nameLen)
		}

		/** Parse supported_versions extension (type 0x002b) for ClientHello format. */
		fun parseSupportedVersions(data: ByteArray): List<Int> {
			if (data.isEmpty()) return emptyList()
			val listLen = data[0].toInt() and 0xff
			val versions = mutableListOf<Int>()
			var pos = 1
			val end = 1 + listLen
			while (pos + 1 < data.size && pos + 1 < end) {
				versions.add(((data[pos].toInt() and 0xff) shl 8) or (data[pos + 1].toInt() and 0xff))
				pos += 2
			}
			return versions
		}

		/** Parse key_share extension (type 0x0033) — extract group IDs from ClientHello. */
		fun parseKeyShareGroups(data: ByteArray): List<Int> {
			if (data.size < 2) return emptyList()
			val listLen = ((data[0].toInt() and 0xff) shl 8) or (data[1].toInt() and 0xff)
			val groups = mutableListOf<Int>()
			var pos = 2
			val end = 2 + listLen
			while (pos + 3 < data.size && pos + 3 < end) {
				val group = ((data[pos].toInt() and 0xff) shl 8) or (data[pos + 1].toInt() and 0xff)
				groups.add(group)
				pos += 2
				val keyLen = ((data[pos].toInt() and 0xff) shl 8) or (data[pos + 1].toInt() and 0xff)
				pos += 2 + keyLen
			}
			return groups
		}

		/** Parse signature_algorithms extension (type 0x000d). */
		fun parseSignatureAlgorithms(data: ByteArray): List<Int> {
			if (data.size < 2) return emptyList()
			val listLen = ((data[0].toInt() and 0xff) shl 8) or (data[1].toInt() and 0xff)
			val algs = mutableListOf<Int>()
			var pos = 2
			val end = 2 + listLen
			while (pos + 1 < data.size && pos + 1 < end) {
				algs.add(((data[pos].toInt() and 0xff) shl 8) or (data[pos + 1].toInt() and 0xff))
				pos += 2
			}
			return algs
		}

		/** Parse supported_groups extension (type 0x000a). */
		fun parseSupportedGroups(data: ByteArray): List<Int> {
			if (data.size < 2) return emptyList()
			val listLen = ((data[0].toInt() and 0xff) shl 8) or (data[1].toInt() and 0xff)
			val groups = mutableListOf<Int>()
			var pos = 2
			val end = 2 + listLen
			while (pos + 1 < data.size && pos + 1 < end) {
				groups.add(((data[pos].toInt() and 0xff) shl 8) or (data[pos + 1].toInt() and 0xff))
				pos += 2
			}
			return groups
		}
	}
}

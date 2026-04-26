package io.natskt.harness.tls

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIMatcher
import javax.net.ssl.SNIServerName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * Manages JDK SSLServerSocket echo endpoints with various TLS configurations.
 * Each endpoint accepts connections, lets JDK handle the TLS handshake, then echoes data.
 */
class TlsEndpoints(
	private val sslContext: SSLContext,
	private val rsaSslContext: SSLContext,
) {
	data class EndpointInfo(
		val name: String,
		val port: Int,
		val thread: Thread,
	)

	private val endpoints = ConcurrentHashMap<String, EndpointInfo>()

	/** Last captured SNI hostname from the sni_capture endpoint. */
	val lastSniHostname = AtomicReference<String?>(null)

	fun startAll(): Map<String, Int> {
		val ports = mutableMapOf<String, Int>()

		ports["tls_default"] =
			startEndpoint("tls_default") { sslParams ->
				sslParams.protocols = arrayOf("TLSv1.3", "TLSv1.2")
			}

		ports["tls13_only"] =
			startEndpoint("tls13_only") { sslParams ->
				sslParams.protocols = arrayOf("TLSv1.3")
			}

		ports["tls12_only"] =
			startEndpoint("tls12_only") { sslParams ->
				sslParams.protocols = arrayOf("TLSv1.2")
			}

		ports["tls13_aes128"] =
			startEndpoint("tls13_aes128") { sslParams ->
				sslParams.protocols = arrayOf("TLSv1.3")
				sslParams.cipherSuites = arrayOf("TLS_AES_128_GCM_SHA256")
			}

		ports["tls13_aes256"] =
			startEndpoint("tls13_aes256") { sslParams ->
				sslParams.protocols = arrayOf("TLSv1.3")
				sslParams.cipherSuites = arrayOf("TLS_AES_256_GCM_SHA384")
			}

		ports["tls13_chacha"] =
			startEndpoint("tls13_chacha") { sslParams ->
				sslParams.protocols = arrayOf("TLSv1.3")
				sslParams.cipherSuites = arrayOf("TLS_CHACHA20_POLY1305_SHA256")
			}

		ports["sni_capture"] = startSniCaptureEndpoint()

		// TLS 1.3 endpoint that only accepts P-384, forcing HelloRetryRequest
		// when the client initially offers a P-256 key_share.
		ports["tls13_hrr_p384"] =
			startEndpoint("tls13_hrr_p384") { sslParams ->
				sslParams.protocols = arrayOf("TLSv1.3")
				sslParams.namedGroups = arrayOf("secp384r1")
			}

		// TLS 1.2 with ECDHE_ECDSA — requires EC certificate
		ports["tls12_ecdhe_ecdsa"] =
			startEndpoint("tls12_ecdhe_ecdsa") { sslParams ->
				sslParams.protocols = arrayOf("TLSv1.2")
				sslParams.cipherSuites = arrayOf("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
			}

		// TLS 1.2 with ECDHE_RSA — requires RSA certificate
		ports["tls12_ecdhe_rsa"] =
			startEndpoint("tls12_ecdhe_rsa", rsaSslContext) { sslParams ->
				sslParams.protocols = arrayOf("TLSv1.2")
				sslParams.cipherSuites = arrayOf("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
			}

		// Server that sends data then closes gracefully (close_notify)
		ports["server_close"] =
			startEndpoint("server_close", afterHandshake = { sslSocket ->
				val output = sslSocket.outputStream
				output.write("goodbye\n".toByteArray())
				output.flush()
				// SSLSocket.close() sends close_notify per RFC 8446 §6.1
			})

		return ports
	}

	fun stopAll() {
		endpoints.values.forEach { it.thread.interrupt() }
	}

	private fun startEndpoint(
		name: String,
		context: SSLContext = sslContext,
		afterHandshake: ((SSLSocket) -> Unit)? = null,
		configure: (SSLParameters) -> Unit = {},
	): Int {
		val serverSocket = context.serverSocketFactory.createServerSocket(0) as SSLServerSocket
		val params = serverSocket.sslParameters
		configure(params)
		serverSocket.sslParameters = params

		val thread =
			Thread({
				acceptLoop(name, serverSocket, afterHandshake)
			}, "tls-endpoint-$name").apply {
				isDaemon = true
				start()
			}

		endpoints[name] = EndpointInfo(name, serverSocket.localPort, thread)
		return serverSocket.localPort
	}

	private fun startSniCaptureEndpoint(): Int {
		val serverSocket = sslContext.serverSocketFactory.createServerSocket(0) as SSLServerSocket
		val params = serverSocket.sslParameters
		params.protocols = arrayOf("TLSv1.3", "TLSv1.2")
		params.sniMatchers =
			listOf(
				object : SNIMatcher(0) { // 0 = host_name type
					override fun matches(serverName: SNIServerName): Boolean {
						if (serverName is SNIHostName) {
							lastSniHostname.set(serverName.asciiName)
						}
						return true // always accept
					}
				},
			)
		serverSocket.sslParameters = params

		val thread =
			Thread({
				acceptLoop("sni_capture", serverSocket) { sslSocket ->
					// After handshake, send the captured SNI as the first line, then echo
					val sni = lastSniHostname.get() ?: ""
					val output = sslSocket.outputStream
					val sniLine = "SNI=$sni\n".toByteArray()
					output.write(sniLine)
					output.flush()
				}
			}, "tls-endpoint-sni_capture").apply {
				isDaemon = true
				start()
			}

		endpoints["sni_capture"] = EndpointInfo("sni_capture", serverSocket.localPort, thread)
		return serverSocket.localPort
	}

	private fun acceptLoop(
		name: String,
		serverSocket: SSLServerSocket,
		afterHandshake: ((SSLSocket) -> Unit)? = null,
	) {
		while (!Thread.currentThread().isInterrupted) {
			try {
				val socket = serverSocket.accept() as SSLSocket
				Thread({
					handleConnection(name, socket, afterHandshake)
				}, "tls-conn-$name-${socket.port}").apply {
					isDaemon = true
					start()
				}
			} catch (_: Exception) {
				break
			}
		}
	}

	private fun handleConnection(
		name: String,
		socket: SSLSocket,
		afterHandshake: ((SSLSocket) -> Unit)? = null,
	) {
		try {
			socket.use { s ->
				// JDK handles the TLS handshake automatically on first I/O
				s.startHandshake()

				afterHandshake?.invoke(s)

				// Echo loop: read data from client, send it back
				val input = s.inputStream
				val output = s.outputStream
				val buf = ByteArray(16384)
				while (true) {
					val n = input.read(buf)
					if (n == -1) break
					output.write(buf, 0, n)
					output.flush()
				}
			}
		} catch (e: Exception) {
			// Connection errors are expected in test scenarios
			System.err.println("[$name] connection error: ${e.message}")
		}
	}
}

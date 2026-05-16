package harness

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

public object RemoteNatsHarness {
	private val httpClient by lazy { createHttpClient() }

	private fun defaultBaseUrl(): String = DEFAULT_REMOTE_HARNESS_URL

	public suspend fun <T> withServer(
		enableJetStream: Boolean = true,
		enableTls: Boolean = false,
		tlsHandshakeFirst: Boolean = false,
		tlsRequireClientCert: Boolean = false,
		baseUrl: String = defaultBaseUrl(),
		block: suspend (RemoteNatsServer) -> T,
	): T {
		val client = RemoteNatsHarnessClient(httpClient, baseUrl)
		val serverInfo =
			client.createServer(
				enableJetStream = enableJetStream,
				enableTls = enableTls,
				tlsHandshakeFirst = tlsHandshakeFirst,
				tlsRequireClientCert = tlsRequireClientCert,
			)
		println("test using server id: ${serverInfo.id}")
		val server = RemoteNatsServer(client, serverInfo)
		return try {
			block(server)
		} finally {
			server.closeAsync()
		}
	}
}

private val exceptionHandler =
	CoroutineExceptionHandler { _, error ->
		// Failed TLS handshakes (e.g. mTLS rejection by the server) leave Ktor's TLS output
		// coroutine writing to a half-closed socket; the resulting "Broken pipe" surfaces
		// as a stray ClosedWriteChannelException after the test has already observed the
		// connect failure. Treat these IO-tail exceptions as non-fatal so test scenarios
		// that intentionally trigger handshake failures don't fail spuriously.
		val message = error.message.orEmpty()
		val isTeardownIoTail =
			error::class.simpleName == "ClosedWriteChannelException" ||
				message.contains("Broken pipe")
		if (isTeardownIoTail) return@CoroutineExceptionHandler
		throw RuntimeException("unhandled exception", error)
	}

public fun RemoteNatsHarness.runBlocking(
	enableJetStream: Boolean = true,
	enableTls: Boolean = false,
	tlsHandshakeFirst: Boolean = false,
	tlsRequireClientCert: Boolean = false,
	baseUrl: String = DEFAULT_REMOTE_HARNESS_URL,
	block: suspend CoroutineScope.(RemoteNatsServer) -> Unit,
): TestResult =
	runTest(timeout = 2.minutes) {
		withContext(Dispatchers.Default.limitedParallelism(1) + exceptionHandler) {
			withServer(
				enableJetStream = enableJetStream,
				enableTls = enableTls,
				tlsHandshakeFirst = tlsHandshakeFirst,
				tlsRequireClientCert = tlsRequireClientCert,
				baseUrl = baseUrl,
			) { server ->
				block(this, server)
			}
		}
	}

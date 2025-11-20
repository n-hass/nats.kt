package harness

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

public object RemoteNatsHarness {
	private val httpClient by lazy { createHttpClient() }

	private fun defaultBaseUrl(): String = DEFAULT_REMOTE_HARNESS_URL

	public suspend fun <T> withServer(
		enableJetStream: Boolean = true,
		baseUrl: String = defaultBaseUrl(),
		block: suspend (RemoteNatsServer) -> T,
	): T {
		val client = RemoteNatsHarnessClient(httpClient, baseUrl)
		val serverInfo = client.createServer(enableJetStream)
		val server = RemoteNatsServer(client, serverInfo)
		return try {
			block(server)
		} finally {
			server.closeAsync()
		}
	}
}

private val exceptionHandler =
	CoroutineExceptionHandler { context, error ->
		throw RuntimeException("unhandled exception", error)
	}

public fun RemoteNatsHarness.runBlocking(
	enableJetStream: Boolean = true,
	baseUrl: String = DEFAULT_REMOTE_HARNESS_URL,
	block: suspend CoroutineScope.(RemoteNatsServer) -> Unit,
): TestResult =
	runTest {
		withContext(Dispatchers.Default.limitedParallelism(1) + exceptionHandler) {
			withServer(enableJetStream, baseUrl) { server ->
				block(this, server)
			}
		}
	}

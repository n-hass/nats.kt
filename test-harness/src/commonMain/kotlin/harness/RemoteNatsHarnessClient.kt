package harness

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal fun createHttpClient(): HttpClient =
	HttpClient {
		expectSuccess = true
		install(ContentNegotiation) {
			json(
				Json {
					ignoreUnknownKeys = true
				},
			)
		}
		install(HttpTimeout) {
			requestTimeoutMillis = 30_000
			connectTimeoutMillis = 5_000
			socketTimeoutMillis = 30_000
		}
	}

internal class RemoteNatsHarnessClient(
	private val httpClient: HttpClient,
	private val baseUrl: String,
) {
	private val serversPath = "${baseUrl.trimEnd('/')}/servers"

	suspend fun createServer(enableJetStream: Boolean): RemoteNatsServerInfo =
		httpClient
			.put(serversPath) {
				contentType(ContentType.Application.Json)
				setBody(RemoteNatsServerRequest(enableJetStream = enableJetStream))
			}.also {
				if (!it.status.isSuccess()) throw IllegalStateException("Request failed: ${it.status}")
			}.body()

	suspend fun deleteServer(id: String) {
		httpClient.delete("$serversPath/$id")
	}

	suspend fun fetchLogs(
		id: String,
		from: Int,
	): RemoteNatsServerLogs =
		httpClient
			.get("$serversPath/$id/logs") {
				parameter("from", from)
			}.body()
}

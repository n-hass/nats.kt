package io.natskt.harness.daemon

import harness.NatsServerHarness
import harness.RemoteNatsServerInfo
import harness.RemoteNatsServerLogs
import harness.RemoteNatsServerRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main() {
	val host = System.getenv("NATS_HARNESS_HOST") ?: "127.0.0.1"
	val port = (System.getenv("NATS_HARNESS_PORT") ?: "4500").toInt()
	val ttlMillis = (System.getenv("NATS_HARNESS_TTL") ?: "60000").toLong()
	val manager = NatsHarnessManager(ttlMillis)

	Runtime.getRuntime().addShutdownHook(Thread { manager.closeAll() })

	embeddedServer(Netty, port = port, host = host) {
		install(ContentNegotiation) {
			json()
		}
		install(CallLogging)
		install(CORS) {
			allowHost("localhost:8081")
			allowHost("0.0.0.0:8081")
			allowHost("127.0.0.1:8081")
			allowHeader(HttpHeaders.ContentType)
			anyHost()
			anyMethod()
		}
		monitor.subscribe(ApplicationStopping) {
			manager.closeAll()
		}
		harnessRoutes(manager)
	}.start(wait = true)
}

private fun Application.harnessRoutes(manager: NatsHarnessManager) {
	routing {
		get("/health") {
			call.respondText("ok")
		}
		put("/servers") {
			val request = call.receive<RemoteNatsServerRequest>()
			val handle = manager.create(request.enableJetStream)
			log.info("Created ${handle.id}")
			call.respond(handle)
		}
		delete("/servers/{id}") {
			val id = call.parameters["id"]
			if (id == null) {
				call.respondText("missing id", status = HttpStatusCode.BadRequest)
				return@delete
			}
			val removed = manager.close(id)
			if (removed) {
				call.respondText("ok")
			} else {
				call.respondText("not found", status = HttpStatusCode.NotFound)
			}
		}
		get("/servers/{id}/logs") {
			val id = call.parameters["id"]
			if (id == null) {
				call.respondText("missing id", status = HttpStatusCode.BadRequest)
				return@get
			}
			val from = call.request.queryParameters["from"]?.toIntOrNull() ?: 0
			val logs = manager.logs(id, from)
			if (logs == null) {
				call.respondText("not found", status = HttpStatusCode.NotFound)
			} else {
				call.respond(logs)
			}
		}
	}
}

private class NatsHarnessManager(
	private val ttlMillis: Long,
) {
	private companion object {
		private const val START_ATTEMPTS = 3
		private const val RETRY_DELAY_MILLIS = 200L
	}

	private data class ManagedServer(
		val harness: NatsServerHarness,
		val expiry: ScheduledFuture<*>,
	)

	private val scheduler =
		Executors.newSingleThreadScheduledExecutor { runnable ->
			Thread(runnable, "nats-harness-expirer").apply { isDaemon = true }
		}
	private val servers = ConcurrentHashMap<String, ManagedServer>()

	@OptIn(ExperimentalUuidApi::class)
	fun create(enableJetStream: Boolean): RemoteNatsServerInfo {
		val id = Uuid.random().toHexDashString()
		var lastError: Throwable? = null

		repeat(START_ATTEMPTS) { attempt ->
			if (attempt >= 2) {
				Thread.sleep(RETRY_DELAY_MILLIS)
			}

			val harness =
				runCatching {
					NatsServerHarness(enableJetStream = enableJetStream, logId = if (attempt == 0) id else "$id-retry$attempt")
				}.getOrElse {
					lastError = it
					return@repeat
				}

			val expiry =
				scheduler.schedule(
					{
						close(id)
					},
					ttlMillis,
					TimeUnit.MILLISECONDS,
				)
			servers[id] = ManagedServer(harness, expiry)
			return RemoteNatsServerInfo(id = id, tcpUri = harness.uri, websocketUri = harness.websocketUri)
		}

		throw IllegalStateException("failed to start nats-server after $START_ATTEMPTS attempts", lastError)
	}

	fun close(id: String): Boolean {
		val managed = servers.remove(id) ?: return false
		managed.expiry.cancel(false)
		managed.harness.close()
		return true
	}

	fun logs(
		id: String,
		from: Int,
	): RemoteNatsServerLogs? {
		val harness = servers[id]?.harness ?: return null
		val entries = harness.logs
		val start = from.coerceAtLeast(0).coerceAtMost(entries.size)
		val slice = entries.subList(start, entries.size)
		return RemoteNatsServerLogs(nextOffset = entries.size, entries = slice)
	}

	fun closeAll() {
		servers.forEach { (_, managed) ->
			runCatching {
				managed.expiry.cancel(false)
				managed.harness.close()
			}
		}
		servers.clear()
		scheduler.shutdownNow()
	}
}

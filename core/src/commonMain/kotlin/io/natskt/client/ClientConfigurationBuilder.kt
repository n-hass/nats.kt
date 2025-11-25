package io.natskt.client

import io.ktor.http.URLBuilder
import io.natskt.api.Credentials
import io.natskt.client.transport.TransportFactory
import io.natskt.internal.NUID
import io.natskt.internal.OperationSerializerImpl
import io.natskt.internal.connectionCoroutineDispatcher
import io.natskt.internal.platformDefaultTransport
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

public class ClientConfigurationBuilder internal constructor() {
	/**
	 * server URI in the format `nats://my-nats.com:4222`, or `wss://my-nats.com:8888`
	 */
	public var server: String? = null

	/**
	 * A list of servers that this client will connect to.
	 */
	public var servers: Collection<String>? = null

	/**
	 * Configure an authentication provider with an instance of [Credentials]
	 *
	 * Valid types:
	 * - JWT + Nkey [Credentials.Jwt]
	 * - Username + Password [Credentials.Password]
	 * - Creds File [Credentials.File]
	 * - Nkey [Credentials.Nkey]
	 */
	public var authentication: Credentials? = null

	/**
	 * The prefix to use for all request reply subjects.
	 * A generated value is placed after this for each request.
	 *
	 * Defaults to `_INBOX.`
	 */
	public var inboxPrefix: String = "_INBOX."

	/**
	 * Maximum reconnect attempts when disconnection occurs from a connected state.
	 */
	public var maxReconnects: Int? = null

	/**
	 * Maximum control line size (in bytes) sent to the server.
	 *
	 * Most (version >2.9) default to 4096 bytes.
	 */
	public var maxControlLineBytes: Int = 4096

	/**
	 * Time allowed for the connection handshake to succeed.
	 *
	 * If handshake exceeds this time, [io.natskt.api.NatsClient.connect] will fail
	 */
	public var connectTimeout: Duration = 5.seconds

	/**
	 * Waiting time between attempting reconnects
	 */
	public var reconnectDebounce: Duration = 2.seconds

	/**
	 * Limit the size of the internal buffer (in bytes) used for sending messages to the server
	 */
	public var writeBufferLimitBytes: Int = 64 * 1024

	/**
	 * Automatically flush the write buffer at this interval, even if it is not full.
	 *
	 * This sets the write latency ceiling.
	 */
	public var writeFlushInterval: Duration = 5.milliseconds

	/**
	 * Set a limit on the maximum number of parallel requests.
	 *
	 * A value of `null` is no limit.
	 *
	 * If set, will always be coerced to at least `1`.
	 *
	 * Does not affect JetStream consumers that use a persistent request subscription.
	 */
	public var maxParallelRequests: Int? = null

	/**
	 * Tries to connect with TLS first, and forces the server to use TLS.
	 */
	public var tlsRequired: Boolean? = null

	/**
	 * The transport type to use. Will default to TCP on supported platforms, or a WebSocket transport
	 * with the platforms preferred [Ktor client engine](https://ktor.io/docs/client-engines.html#dependencies)
	 */
	public var transport: TransportFactory? = null

	/**
	 * Provide a coroutine scope to launch all client-related jobs in.
	 *
	 * If not provided, a scope will be created with:
	 * - IO Dispatcher [kotlinx.coroutines.Dispatchers] on supported platforms, or [kotlinx.coroutines.Dispatchers.Default] for JS platforms
	 * - Running as a [SupervisorJob]
	 */
	public var scope: CoroutineScope? = null
}

private val secureProtocols =
	listOf(
		"tls",
		"wss",
	)

internal fun ClientConfigurationBuilder.build(): ClientConfiguration {
	val serversList =
		buildList {
			servers?.forEach {
				add(parseUrl(it))
			}
			server?.let { add(parseUrl(it)) }
		}.also {
			if (it.isEmpty()) error("must provide at least one server")
		}

	val inboxPrefix = if (inboxPrefix.endsWith(".")) inboxPrefix else "$inboxPrefix."

	val tls = this.tlsRequired ?: serversList.any { secureProtocols.contains(it.url.protocol.name) }
	val finalScope = scope ?: CoroutineScope(connectionCoroutineDispatcher + SupervisorJob() + CoroutineName("NatsClient"))
	val parallelRequestLimit =
		maxParallelRequests?.also {
			require(it > 0) {
				"maxParallelRequests must be > 0"
			}
		}

	return ClientConfiguration(
		servers = serversList,
		transportFactory = transport ?: platformDefaultTransport,
		credentials = authentication,
		inboxPrefix = inboxPrefix,
		parser = OperationSerializerImpl(),
		maxReconnects = maxReconnects,
		connectTimeoutMs = connectTimeout.inWholeMilliseconds,
		reconnectDebounceMs = reconnectDebounce.inWholeMilliseconds,
		maxControlLineBytes = maxControlLineBytes,
		writeBufferLimitBytes = writeBufferLimitBytes,
		writeFlushIntervalMs = writeFlushInterval.inWholeMilliseconds,
		tlsRequired = tls,
		maxParallelRequests = parallelRequestLimit,
		nuid = NUID.Default,
		scope = finalScope,
		ownsScope = scope == null,
	)
}

private fun parseUrl(raw: String): NatsServerAddress =
	NatsServerAddress(
		URLBuilder(raw)
			.build(),
	)

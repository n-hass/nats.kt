package io.natskt.client

import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.write
import io.natskt.api.Connection
import io.natskt.client.transport.Transport
import io.natskt.internal.api.ConnectOp
import io.natskt.internal.api.InfoOp
import io.natskt.internal.api.Operation
import io.natskt.internal.api.Ping
import io.natskt.internal.api.ServerOperation
import io.natskt.internal.connectionCoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

public enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
}

internal class ConnectionImpl(
    private val options: ClientConfiguration,
) : Connection {
    val scope: CoroutineScope = CoroutineScope(connectionCoroutineDispatcher + SupervisorJob() + CoroutineName("Connector"))

    val incoming: SharedFlow<Operation>
        get() = _incoming
    private val _incoming = MutableSharedFlow<Operation>()

    val state: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)

    private var hasConnected: Boolean = false

    override suspend fun connect() {
        val server = options.servers.first()
        println("connecting to $server")
        state.emit(ConnectionState.Connecting)
        val ts = options.transportFactory.connect(server, currentCoroutineContext())
        state.emit(ConnectionState.Connected)

        scope.launch {
            options.parser.parse(ts.incoming, this).collect {
                when(it) {
                    is InfoOp -> {
                        if (!hasConnected) {
                            val connect = ConnectOp(
                                verbose = true,
                                pedantic = false,
                                tlsRequired = false,
                                authToken = null,
                                user = null,
                                pass = null,
                                name = null,
                                protocol = null,
                                echo = false,
                                sig = null,
                                jwt = null,
                                noResponders = null,
                                headers = null,
                                nkey =null
                            )
                            val msg = "CONNECT ${Json {encodeDefaults = true}.encodeToString(connect)}\r\n".toByteArray()
                            ts.write(msg)
                        }
                    }
                    is Ping -> {
                        val msg = "PONG\r\n".toByteArray()
                        ts.write(msg)
                    }
                    else -> _incoming.emit(it)
                }
                _incoming.emit(it)
            }
        }
    }
}

private suspend fun Transport.write(msg: ByteArray) {
    var written = 0
    write {
        while(written < msg.size) {
            it.write(msg.size - written) { buffer, low, high ->
                var writtenHere = 0
                for(i in low..high) {
                    if (written >= msg.size) break
                    buffer[i] = msg[written]
                    written++
                    writtenHere++
                }
                writtenHere
            }
        }
    }
    flush()
}

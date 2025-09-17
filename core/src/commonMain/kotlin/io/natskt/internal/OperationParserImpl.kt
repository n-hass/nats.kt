package io.natskt.internal

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import io.natskt.internal.api.Ack
import io.natskt.internal.api.InfoOp
import io.natskt.internal.api.Ok
import io.natskt.internal.api.Operation
import io.natskt.internal.api.OperationParser
import io.natskt.internal.api.Ping
import io.natskt.internal.api.ServerOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json

internal class OperationParserImpl : OperationParser {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        classDiscriminatorMode = ClassDiscriminatorMode.NONE
        explicitNulls = false
    }

    override suspend fun parse(channel: ByteReadChannel, shareIn: CoroutineScope): SharedFlow<Operation> =
        flow {
            while(!channel.isClosedForRead) {
                val control = channel.readUTF8Line() ?: return@flow
                println("raw: $control")

                if (control[0] == '+') {
                    when(control) {
                        "+OK" ->  { emit(Ok); continue }
                        "+ACK" -> { emit(Ack); continue }
                    }
                }

                if (control == "PING") {
                    emit(Ping)
                    continue
                }

                var i = 0
                val op = StringBuilder()
                while (control[i] != ' ') {
                    op.append(control[i])
                    i++
                }
                val msg = control.substring(i)


                val parsed: ServerOperation? = when(op.toString()) {
                    "INFO" -> json.decodeFromString<InfoOp>(msg)
                    else -> null
                }

                if (parsed == null) {
                    println("received unparseable control. op: $op, msg: $msg")
                    continue
                }

                emit(parsed)
            }
        }.shareIn(
            shareIn,
            SharingStarted.Eagerly,
            0
        )
}

package io.natskt.internal

import io.natskt.api.internal.ClientOperation
import io.natskt.api.internal.Operation
import io.natskt.api.internal.OperationSerializer
import io.natskt.api.internal.ServerOperation

private const val LINE_END = "\r\n"

internal class OperationSerializerImpl : OperationSerializer {
	override fun parseOrNull(line: String): Operation? {
		println("raw: $line")
		if (line == "PONG") {
			return Operation.Pong
		}

		if (line == "PING") {
			return Operation.Ping
		}

		if (line[0] == '+') {
			when (line) {
				"+OK" -> {
					return Operation.Ok
				}
				"+ERR" -> {
					return Operation.Err
				}
			}
		}

		var i = 0
		val op = StringBuilder()
		while (line[i] != ' ') {
			op.append(line[i])
			i++
		}
		val msg = line.substring(i)

		val parsed: ServerOperation? =
			when (op.toString()) {
				"INFO" -> wireJsonFormat.decodeFromString<ServerOperation.InfoOp>(msg)
				else -> null
			}

		if (parsed == null) {
			println("received unparseable control. op: $op, msg: $msg")
			return null
		}

		return parsed
	}

	override fun encode(op: ClientOperation): String =
		when (op) {
			Operation.Ping -> "PING"
			Operation.Pong -> "PONG"
			is ClientOperation.ConnectOp -> {
				"CONNECT ${wireJsonFormat.encodeToString(op)}"
			}
		} + LINE_END
}

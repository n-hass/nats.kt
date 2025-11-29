package io.natskt.internal

import io.ktor.utils.io.core.toByteArray

const val LINE_END = "\r\n"
val LINE_END_BYTES = LINE_END.encodeToByteArray()

val PING = "PING".toByteArray()
val PONG = "PONG".toByteArray()
val OK = "+OK".toByteArray()
val ERR = "-ERR".toByteArray()
val INFO = "INFO".toByteArray()
val MSG = "MSG".toByteArray()
val HMSG = "HMSG".toByteArray()
const val DOUBLE_LINE_END = "$LINE_END$LINE_END"
val pingOpBytes = "PING".toByteArray()
val pongOpBytes = "PONG".toByteArray()
val connectOpBytes = "CONNECT ".toByteArray()
val pubOpBytes = "PUB ".toByteArray()
val hpubOpBytes = "HPUB ".toByteArray()
val subOpBytes = "SUB ".toByteArray()
val unsubOpBytes = "UNSUB ".toByteArray()

val empty = "".toByteArray()

const val SPACE_BYTE = ' '.code.toByte()
const val CR_BYTE = '\r'.code.toByte()
const val LF_BYTE = '\n'.code.toByte()
const val CR_CODE = '\r'.code.toLong()
const val LF_CODE = '\n'.code.toLong()

const val HEADER_START = "NATS/1.0"
val HEADER_START_BYTES = HEADER_START.encodeToByteArray()
val COLON_SPACE_BYTES = ": ".encodeToByteArray()

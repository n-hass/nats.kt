@file:OptIn(ExperimentalForeignApi::class)

package io.natskt.client.transport.internal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.selector.Selectable
import io.ktor.network.sockets.Connection
import io.natskt.client.SocketKeepAliveConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.sizeOf
import platform.posix.IPPROTO_TCP
import platform.posix.SOL_SOCKET
import platform.posix.SO_KEEPALIVE
import platform.posix.TCP_KEEPCNT
import platform.posix.TCP_KEEPINTVL
import platform.posix.errno
import platform.posix.setsockopt
import platform.posix.socklen_t

private val logger = KotlinLogging.logger("SocketKeepAlive")

/**
 * Platform constant for TCP keepalive idle time. Darwin uses `TCP_KEEPALIVE`; Linux uses `TCP_KEEPIDLE`.
 */
internal expect val TCP_KEEPALIVE_IDLE: Int

/**
 * Factor to convert seconds to the platform's expected `setsockopt` units
 * (Darwin uses milliseconds, Linux uses seconds).
 */
internal expect val TCP_KEEPALIVE_FACTOR: Int

internal actual fun tuneSocketKeepAlive(
	connection: Connection,
	config: SocketKeepAliveConfig,
) {
	val fd =
		(connection.socket as? Selectable)?.descriptor
			?: run {
				logger.debug { "tuneSocketKeepAlive: Ktor socket is not Selectable; skipping" }
				return
			}
	val optLen: socklen_t = sizeOf<IntVar>().convert()
	val idleTime = (config.idle.inWholeSeconds * TCP_KEEPALIVE_FACTOR).toInt()
	val intervalTime = (config.interval.inWholeSeconds * TCP_KEEPALIVE_FACTOR).toInt()
	if (setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, cValuesOf(1), optLen) != 0) {
		logger.debug { "setsockopt SO_KEEPALIVE failed fd=$fd errno=$errno" }
		return
	}
	if (setsockopt(fd, IPPROTO_TCP, TCP_KEEPALIVE_IDLE, cValuesOf(idleTime), optLen) != 0) {
		logger.debug { "setsockopt TCP_KEEPALIVE_IDLE=${config.idle} failed fd=$fd errno=$errno" }
	}
	if (setsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, cValuesOf(intervalTime), optLen) != 0) {
		logger.debug { "setsockopt TCP_KEEPINTVL=${config.interval} failed fd=$fd errno=$errno" }
	}
	if (setsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, cValuesOf(config.probeCount), optLen) != 0) {
		logger.debug { "setsockopt TCP_KEEPCNT=${config.probeCount} failed fd=$fd errno=$errno" }
	}
}

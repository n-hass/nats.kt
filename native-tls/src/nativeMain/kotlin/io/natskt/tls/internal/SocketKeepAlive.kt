@file:OptIn(ExperimentalForeignApi::class)

package io.natskt.tls.internal

import io.github.oshai.kotlinlogging.KotlinLogging
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
import kotlin.time.Duration

private val logger = KotlinLogging.logger("SocketKeepAlive")

/**
 * Platform constant for TCP keepalive idle time. Darwin uses `TCP_KEEPALIVE`; Linux uses `TCP_KEEPIDLE`.
 */
internal expect val TCP_KEEPALIVE_IDLE: Int

/**
 * The factor to convert from seconds to the platform's expected `setsockopt` units
 * e.g. macOS uses milliseconds, linux uses seconds
 */
internal expect val TCP_KEEPALIVE_FACTOR: Int

/**
 * Enables SO_KEEPALIVE on [fd] and tunes idle/interval/count so dead connections are detected within
 * `idleSeconds + intervalSeconds * probeCount` seconds. Each setsockopt failure is logged but does
 * not abort: SO_KEEPALIVE alone (with OS defaults for the timers) is still preferable to nothing.
 */
internal fun configureTcpKeepAlive(
	fd: Int,
	idleSeconds: Duration,
	intervalSeconds: Duration,
	probeCount: Int,
) {
	val optLen: socklen_t = sizeOf<IntVar>().convert()
	val idleTime = (idleSeconds.inWholeSeconds * TCP_KEEPALIVE_FACTOR).toInt()
	val intervalTime = (intervalSeconds.inWholeSeconds * TCP_KEEPALIVE_FACTOR).toInt()
	if (setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, cValuesOf(1), optLen) != 0) {
		logger.debug { "setsockopt SO_KEEPALIVE failed fd=$fd errno=$errno" }
		return
	}
	if (setsockopt(fd, IPPROTO_TCP, TCP_KEEPALIVE_IDLE, cValuesOf(idleTime), optLen) != 0) {
		logger.debug { "setsockopt TCP_KEEPALIVE_IDLE=$idleSeconds failed fd=$fd errno=$errno" }
	}
	if (setsockopt(fd, IPPROTO_TCP, TCP_KEEPINTVL, cValuesOf(intervalTime), optLen) != 0) {
		logger.debug { "setsockopt TCP_KEEPINTVL=$intervalSeconds failed fd=$fd errno=$errno" }
	}
	if (setsockopt(fd, IPPROTO_TCP, TCP_KEEPCNT, cValuesOf(probeCount), optLen) != 0) {
		logger.debug { "setsockopt TCP_KEEPCNT=$probeCount failed fd=$fd errno=$errno" }
	}
}

@file:OptIn(ExperimentalForeignApi::class)

package io.natskt.tls.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.TCP_KEEPIDLE

internal actual val TCP_KEEPALIVE_IDLE: Int = TCP_KEEPIDLE
internal actual val TCP_KEEPALIVE_FACTOR: Int = 1

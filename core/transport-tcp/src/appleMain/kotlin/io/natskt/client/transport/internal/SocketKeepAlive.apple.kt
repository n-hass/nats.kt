@file:OptIn(ExperimentalForeignApi::class)

package io.natskt.client.transport.internal

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.TCP_KEEPALIVE

internal actual val TCP_KEEPALIVE_IDLE: Int = TCP_KEEPALIVE
internal actual val TCP_KEEPALIVE_FACTOR: Int = 1_000

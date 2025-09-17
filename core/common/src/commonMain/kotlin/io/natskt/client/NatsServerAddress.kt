package io.natskt.client

import io.ktor.http.Url
import kotlin.jvm.JvmInline

@JvmInline
public value class NatsServerAddress(
    public val url: Url,
)

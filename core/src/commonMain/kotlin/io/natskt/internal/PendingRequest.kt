package io.natskt.internal

import io.natskt.api.Message
import kotlinx.coroutines.CancellableContinuation
import kotlin.jvm.JvmInline

@JvmInline
internal value class PendingRequest(
	val continuation: CancellableContinuation<Message>,
)

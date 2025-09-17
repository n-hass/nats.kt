package io.natskt.internal.api

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow

public interface OperationParser {
    public suspend fun parse(channel: ByteReadChannel, shareIn: CoroutineScope): SharedFlow<Operation>
}

package io.natskt.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val connectionCoroutineDispatcher: CoroutineDispatcher = Dispatchers.Default

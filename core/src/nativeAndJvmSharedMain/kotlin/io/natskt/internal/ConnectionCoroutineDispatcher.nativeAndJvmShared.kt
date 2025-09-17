package io.natskt.internal

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val connectionCoroutineDispatcher: CoroutineDispatcher = Dispatchers.IO

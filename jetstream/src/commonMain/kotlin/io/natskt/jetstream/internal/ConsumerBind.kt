package io.natskt.jetstream.internal

import io.natskt.jetstream.api.ConsumerInfo
import kotlin.contracts.ExperimentalContracts

@OptIn(ExperimentalContracts::class)
internal fun ConsumerInfo.isPush(): Boolean = config.deliverSubject != null

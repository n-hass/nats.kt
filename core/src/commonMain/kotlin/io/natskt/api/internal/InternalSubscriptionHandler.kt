package io.natskt.api.internal

import io.natskt.api.Message
import io.natskt.api.Subscription

internal interface InternalSubscriptionHandler : Subscription {
	suspend fun emit(msg: Message)
}

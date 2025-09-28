package io.natskt.internal

import io.natskt.api.Message
import io.natskt.api.Subscription

interface InternalSubscriptionHandler : Subscription {
	suspend fun emit(msg: Message)
}

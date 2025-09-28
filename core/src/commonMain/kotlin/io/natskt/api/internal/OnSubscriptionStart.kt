package io.natskt.api.internal

import io.natskt.internal.InternalSubscriptionHandler

internal fun interface OnSubscriptionStart {
	suspend operator fun invoke(
		sub: InternalSubscriptionHandler,
		sid: String,
		subject: String,
		queueGroup: String?,
	)
}

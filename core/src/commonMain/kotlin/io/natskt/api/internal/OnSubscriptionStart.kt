package io.natskt.api.internal

internal fun interface OnSubscriptionStart {
	suspend operator fun invoke(
		sub: InternalSubscriptionHandler,
		sid: String,
		subject: String,
		queueGroup: String?,
	)
}

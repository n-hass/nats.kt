package io.natskt.api.internal

internal fun interface OnSubscriptionStop {
	suspend operator fun invoke(
		sid: String,
		maxMsgs: Int?,
	)
}

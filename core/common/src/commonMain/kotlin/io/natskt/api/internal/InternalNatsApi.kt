package io.natskt.api.internal

@RequiresOptIn(
	message = "This is only intended for use internally by the NATS library and will likely be removed from the public API surface in a future update",
	level = RequiresOptIn.Level.ERROR,
)
public annotation class InternalNatsApi

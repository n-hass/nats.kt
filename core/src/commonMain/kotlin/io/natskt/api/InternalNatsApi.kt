package io.natskt.api

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This is exposes state or APIs internal to the client implementation - it may change or be fragile.",
)
public annotation class InternalNatsApi

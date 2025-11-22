package io.natskt.jetstream.internal

import io.natskt.api.Message
import io.natskt.internal.MessageInternal
import io.natskt.jetstream.api.JetStreamClient

internal fun wrapJetstreamMessage(
	msg: Message,
	js: JetStreamClient,
) = IncomingJetStreamMessage(
	original = msg as? MessageInternal ?: throw RuntimeException("internal error: Message cast fail"),
	client = js.client,
)

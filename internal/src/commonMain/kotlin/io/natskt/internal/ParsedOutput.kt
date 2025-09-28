package io.natskt.internal

import io.natskt.api.JetStreamMessage
import io.natskt.api.Message

sealed interface ParsedOutput

interface MessageInternal :
	Message,
	ParsedOutput {
	val sid: String
}

interface JetStreamMessageInternal :
	JetStreamMessage,
	ParsedOutput {
	val sid: String
}

package io.natskt.internal

import io.natskt.api.Message

sealed interface ParsedOutput

interface MessageInternal :
	Message,
	ParsedOutput {
	val sid: String
}

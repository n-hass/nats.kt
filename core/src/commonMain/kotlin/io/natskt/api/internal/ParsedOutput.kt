package io.natskt.api.internal

import io.natskt.api.Message

internal sealed interface ParsedOutput

internal interface MessageInternal :
	Message,
	ParsedOutput {
	val sid: String
	override var ackWait: (suspend () -> Unit)?
	override var ack: (() -> Unit)?
	override var nak: (() -> Unit)?
}

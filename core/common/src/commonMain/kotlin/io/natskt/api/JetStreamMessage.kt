package io.natskt.api

public typealias AckAction = suspend () -> Unit

public interface JetStreamMessage : Message {
	public val ack: AckAction
	public val ackWait: AckAction
	public val nak: AckAction
}

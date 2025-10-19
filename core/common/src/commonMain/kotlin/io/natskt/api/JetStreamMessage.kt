package io.natskt.api

public interface JetStreamMessage : Message {
	public suspend fun ack()

	public suspend fun ackWait()

	public suspend fun nak()

	public suspend fun inProgress()

	public suspend fun term()
}

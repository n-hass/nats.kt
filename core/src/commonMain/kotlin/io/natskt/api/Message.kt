package io.natskt.api

import io.natskt.internal.Subject

public interface Message {
	public val subject: Subject
	public val replyTo: Subject?
	public val headers: Map<String, List<String>>?
	public val data: ByteArray?
	public val ack: (() -> Unit)?
	public val ackWait: (suspend () -> Unit)?
	public val nak: (() -> Unit)?
}

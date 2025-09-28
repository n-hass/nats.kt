package io.natskt.api

public interface Message {
	public val subject: Subject
	public val replyTo: Subject?
	public val headers: Map<String, List<String>>?
	public val data: ByteArray?
}

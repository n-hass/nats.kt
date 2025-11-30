package io.natskt.api

public interface Message {
	public val subject: Subject
	public val replyTo: Subject?
	public val headers: MessageHeaders?
	public val data: ByteArray?
	public val status: Int?
}

public typealias MessageHeaders = Map<String, List<String>>

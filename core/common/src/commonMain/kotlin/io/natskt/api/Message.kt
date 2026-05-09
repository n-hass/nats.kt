package io.natskt.api

public interface Message {
	public val subject: Subject
	public val replyTo: Subject?
	public val headers: MessageHeaders?
	public val data: ByteArray?
	public val status: Int?

	/**
	 * Human-readable description portion of a NATS status line (e.g. "No Messages",
	 * "Consumer Deleted"). Null when [status] is null or no description was provided.
	 */
	public val statusDescription: String? get() = null
}

public typealias MessageHeaders = Map<String, List<String>>

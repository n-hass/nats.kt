package io.natskt.client

import io.natskt.api.Message
import io.natskt.internal.OutgoingMessage
import io.natskt.internal.Subject

public class ByteMessageBuilder {
	public var subject: String? = null
	public var replyTo: String? = null
	public var headers: Map<String, List<String>>? = null
	public var data: ByteArray? = null
}

public class StringMessageBuilder {
	public var subject: String? = null
	public var replyTo: String? = null
	public var headers: Map<String, List<String>>? = null
	public var data: String? = null
}

internal fun ByteMessageBuilder.build(): Message =
	OutgoingMessage(
		subject = this.subject?.let { Subject(it) } ?: error("subject must be set"),
		replyTo = this.replyTo?.let { Subject(it) },
		headers = this.headers,
		data = this.data,
	)

internal fun StringMessageBuilder.build(): Message =
	OutgoingMessage(
		subject = this.subject?.let { Subject(it) } ?: error("subject must be set"),
		replyTo = this.replyTo?.let { Subject(it) },
		headers = this.headers,
		data = this.data?.encodeToByteArray(),
	)

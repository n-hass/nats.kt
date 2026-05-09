package io.natskt.api

import io.natskt.api.internal.InternalNatsApi
import kotlin.jvm.JvmInline

@JvmInline
public value class SubjectToken(
	public val token: String,
) {
	public companion object
}

@OptIn(InternalNatsApi::class)
@Throws(InvalidSubjectException::class)
public fun SubjectToken.Companion.from(s: String): SubjectToken {
	if (s.isEmpty()) throw InvalidSubjectException("a blank string is not a valid subject token")
	if (isInvalidToken(s)) throw InvalidSubjectException(s, "$s is not a valid subject token")
	return SubjectToken(s)
}

/**
 * Returns `true` if [s] is not a valid token (single subject token, stream name,
 * consumer name, or KV bucket name).
 *
 * Mirrors nats.go's `validateStreamName` / `validateConsumerName`: rejects
 * `>`, `*`, `.`, ` ` (space), `/`, `\`, plus tab/CR/LF for safety.
 *
 * Empty strings are also invalid.
 *
 * UTF-8 characters are permitted; the server enforces `utf8_only` separately.
 */
@InternalNatsApi
public fun isInvalidToken(s: String): Boolean {
	if (s.isEmpty()) return true
	for (c in s) {
		when (c) {
			' ', '\t', '\r', '\n', '.', '*', '>', '/', '\\' -> return true
		}
	}
	return false
}

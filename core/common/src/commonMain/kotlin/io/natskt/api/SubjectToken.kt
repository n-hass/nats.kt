package io.natskt.api

import io.natskt.api.internal.InternalNatsApi
import kotlin.jvm.JvmInline

private val disallowedTokenChars = Regex("[\\u0000 .*>\\t\\r\\n]")

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

@InternalNatsApi
public fun isInvalidToken(s: String): Boolean = disallowedTokenChars.containsMatchIn(s)

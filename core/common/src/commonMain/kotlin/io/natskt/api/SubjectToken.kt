package io.natskt.api

import kotlin.jvm.JvmInline

private val disallowedNatsChars = Regex("[\\u0000 .*>]")

@JvmInline
public value class SubjectToken(
	public val token: String,
) {
	public companion object
}

public fun SubjectToken.Companion.from(s: String): SubjectToken {
	if (s.isEmpty()) throw IllegalArgumentException("a blank string is not a valid subject token")
	if (disallowedNatsChars.matches(s)) throw IllegalArgumentException("$s is not a valid subject token")
	return SubjectToken(s)
}

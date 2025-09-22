package io.natskt.internal

import kotlin.jvm.JvmInline

private val disallowedSubjectChars = Regex("[\\u0000 ]")

@JvmInline
public value class Subject internal constructor(
	public val raw: String,
) {
	public companion object { }
}

public fun Subject.Companion.fromOrNull(s: String): Subject? {
	if (disallowedSubjectChars.containsMatchIn(s)) return null

	return Subject(s)
}

public fun Subject.Companion.from(s: String): Subject = fromOrNull(s) ?: throw IllegalArgumentException("'$s' contains invalid subject token characters")

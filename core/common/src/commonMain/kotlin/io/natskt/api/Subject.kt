package io.natskt.api

import io.natskt.api.internal.InternalNatsApi
import kotlin.jvm.JvmInline

private val disallowedSubjectChars = Regex("[\\u0000 ]")
private val wildcardChars = Regex("[*>]")

@JvmInline
public value class Subject
	@InternalNatsApi
	constructor(
		public val raw: String,
	) {
		public companion object { }
	}

@OptIn(InternalNatsApi::class)
public fun Subject.Companion.fromOrNull(s: String): Subject? {
	if (disallowedSubjectChars.containsMatchIn(s)) return null

	return Subject(s)
}

public fun Subject.Companion.from(s: String): Subject = fromOrNull(s) ?: throw IllegalArgumentException("'$s' contains invalid subject token characters")

/**
 * Create a subject from a fully-qualified string (contains no wildcards)
 * @throws IllegalArgumentException when invalid characters or a wildcard are included
 */
@OptIn(InternalNatsApi::class)
public fun Subject.Companion.fullyQualified(s: String): Subject {
	if (disallowedSubjectChars.containsMatchIn(s) || wildcardChars.containsMatchIn(s)) {
		throw IllegalArgumentException("'$s' must not contain wildcards, or it has invalid chars")
	}

	return Subject(s)
}

@InternalNatsApi
public fun validateSubject(s: String): Boolean = disallowedSubjectChars.containsMatchIn(s)

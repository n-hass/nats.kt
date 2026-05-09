package io.natskt.api

import io.natskt.api.internal.InternalNatsApi
import kotlin.jvm.JvmInline

public class InvalidSubjectException(
	subject: String,
	message: String? = null,
) : IllegalArgumentException(
		message ?: "`$subject` is not a valid NATS subject",
	)

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
	if (isInvalidSubject(s)) return null

	return Subject(s)
}

public fun Subject.Companion.from(s: String): Subject = fromOrNull(s) ?: throw InvalidSubjectException(s)

/**
 * Create a subject from a fully-qualified string (contains no wildcards)
 * @throws IllegalArgumentException when invalid characters or a wildcard are included
 */
@OptIn(InternalNatsApi::class)
public fun Subject.Companion.fullyQualified(s: String): Subject {
	if (isInvalidFullyQualifiedSubject(s)) {
		throw InvalidSubjectException(s, message = "'$s' must be a valid subject and not contain wildcards")
	}

	return Subject(s)
}

/**
 * Returns `true` if [s] is not a valid NATS subject.
 *
 * rejects whitespace (space, tab, CR, LF) and any subject that has
 * empty tokens (leading dot, trailing dot, or consecutive dots).
 *
 * UTF-8 characters are permitted; the server enforces `utf8_only` separately.
 */
@InternalNatsApi
public fun isInvalidSubject(s: String): Boolean {
	if (s.isEmpty()) return true
	var tokenStart = 0
	for (i in s.indices) {
		when (s[i]) {
			' ', '\t', '\r', '\n' -> return true
			'.' -> {
				if (i == tokenStart) return true
				tokenStart = i + 1
			}
		}
	}
	return tokenStart == s.length
}

/**
 * Returns `true` if [s] is not a valid fully-qualified (wildcard-free) NATS subject.
 *
 * Same checks as [isInvalidSubject], plus rejects the `*` and `>` wildcard tokens.
 */
@InternalNatsApi
public fun isInvalidFullyQualifiedSubject(s: String): Boolean {
	if (isInvalidSubject(s)) return true
	for (c in s) {
		if (c == '*' || c == '>') return true
	}
	return false
}

package io.natskt.api

import io.natskt.api.internal.InternalNatsApi
import kotlin.jvm.JvmInline

private val disallowedSubjectChars = Regex("[\\u0000 \\t\\r\\n]")
private val wildcardChars = Regex("[*>]")

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

@InternalNatsApi
public fun isInvalidSubject(s: String): Boolean = disallowedSubjectChars.containsMatchIn(s)

@InternalNatsApi
public fun isInvalidFullyQualifiedSubject(s: String): Boolean = disallowedSubjectChars.containsMatchIn(s) || wildcardChars.containsMatchIn(s)

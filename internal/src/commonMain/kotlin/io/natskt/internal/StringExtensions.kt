package io.natskt.internal

import io.natskt.api.InvalidSubjectException
import io.natskt.api.internal.InternalNatsApi
import io.natskt.api.isInvalidFullyQualifiedSubject
import io.natskt.api.isInvalidSubject
import io.natskt.api.isInvalidToken

@OptIn(InternalNatsApi::class)
fun String.throwOnInvalidToken() {
	if (isInvalidToken(this)) {
		throw InvalidSubjectException(this, "not a valid token")
	}
}

@OptIn(InternalNatsApi::class)
fun String.throwOnInvalidSubject() {
	if (isInvalidSubject(this)) {
		throw InvalidSubjectException(this, "not a valid subject")
	}
}

@OptIn(InternalNatsApi::class)
fun String.throwOnInvalidFullyQualifiedSubject() {
	if (isInvalidFullyQualifiedSubject(this)) {
		throw InvalidSubjectException(this, "not a valid fully qualified subject")
	}
}

package io.natskt.api

public data class AuthPayload(
	val authToken: String? = null,
	val user: String? = null,
	val pass: String? = null,
	val jwt: String? = null,
	val signature: String? = null,
	val nkeyPublic: String? = null,
)

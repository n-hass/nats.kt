package io.natskt.api

public data class AuthPayload(
	val authToken: String? = null,
	val username: String? = null,
	val password: String? = null,
	val jwt: String? = null,
	val signature: String? = null,
	/**
	 * Public key for server to verify
	 */
	val nkey: String? = null,
)

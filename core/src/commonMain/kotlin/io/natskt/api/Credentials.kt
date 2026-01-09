package io.natskt.api

public sealed interface Credentials {
	public data class Jwt(
		val token: String,
		val nkey: String,
	) : Credentials

	public data class Password(
		val username: String,
		val password: String,
	) : Credentials

	public data class File(
		val content: String,
	) : Credentials

	public data class Nkey(
		val key: String,
	) : Credentials

	public data class Custom(
		val jwt: Jwt? = null,
		val password: Password? = null,
		val file: File? = null,
		val nkey: Nkey? = null,
	) : Credentials
}

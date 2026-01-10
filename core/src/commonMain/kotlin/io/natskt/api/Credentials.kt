package io.natskt.api

public sealed interface Credentials {
	/**
	 * Use a JWT for identification as a user and nkey to sign the server nonce.
	 */
	public data class Jwt(
		val token: String,
		val nkey: String,
	) : Credentials

	/**
	 * Use an explicit username and password.
	 */
	public data class Password(
		val username: String,
		val password: String,
	) : Credentials

	/**
	 * Use a JWT and nkey, read from a standard NATS creds file. You must read and supply the file content.
	 *
	 * Identical to [Credentials.Jwt] in function, but reads credentials in the format:
	 * ```txt
	 * -----BEGIN NATS USER JWT-----
	 * ey...
	 * ------END NATS USER JWT------
	 *
	 * ************************* IMPORTANT *************************
	 * NKEY Seed printed below can be used to sign and prove identity.
	 * NKEYs are sensitive and should be treated as secrets.
	 *
	 * -----BEGIN USER NKEY SEED-----
	 * SU...
	 * ------END USER NKEY SEED------
	 *
	 * *************************************************************
	 * ```
	 */
	public data class File(
		val content: String,
	) : Credentials

	/**
	 * Use a standalone nkey (without JWT) to sign the server nonce
	 */
	public data class Nkey(
		val key: String,
	) : Credentials

	/**
	 * Use an [AuthProvider], which returns an [AuthPayload]. This allows full control over the authentication parameters sent to the server.
	 *
	 * Your [AuthProvider] will be called with a [AuthProviderScope] and [ServerInfo].
	 *
	 * ### Signature
	 * If you require a signature to be set (such as for the server's standard NKey challenge/response), you must set this yourself.
	 *
	 * [AuthProviderScope] contains [AuthProviderScope.signNonce] and [AuthProviderScope.signWithNkey], which you can use to create a signature when required.
	 *
	 * ### Example
	 *
	 * ```
	 * val provider: Credentials.AuthProvider = Credentials.AuthProvider { info ->
	 * 	val mySecret = ""
	 * 	AuthPayload(
	 * 		jwt = "ey...",
	 * 		nkeyPublic = "UB...",
	 * 		signature = signNonce(mySecret, info) // <- This comes from AuthProviderScope
	 * 	)
	 * }
	 * ```
	 */
	public data class Custom(
		val provider: AuthProvider,
	) : Credentials
}

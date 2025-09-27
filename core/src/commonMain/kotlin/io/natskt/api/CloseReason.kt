package io.natskt.api

public sealed interface CloseReason {
	public data object CleanClose : CloseReason

	public data class ProtocolError(
		val message: String?,
	) : CloseReason

	public data class IoError(
		val cause: Throwable,
	) : CloseReason

	public data object ServerInitiatedClose : CloseReason

	public data object MaxControlLineExceeded : CloseReason

	public data object PayloadTooLarge : CloseReason

	public data object HandshakeRejected : CloseReason

	public data object LameDuckMode : CloseReason
}

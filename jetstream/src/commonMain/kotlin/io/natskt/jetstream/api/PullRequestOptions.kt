package io.natskt.jetstream.api

public data class PullRequestOptions(
	val batch: Int = 1,
	val maxBytes: Long? = null,
	val expires: Long? = 5_000,
	val noWait: Boolean = false,
	val idleHeartbeat: Long? = null,
	val requestTimeoutMs: Long = 6_000,
) {
	init {
		require(batch > 0) { "batch must be greater than zero" }
		require(requestTimeoutMs > 0) { "requestTimeoutMs must be greater than zero" }
	}
}

package io.natskt.jetstream.api

import kotlinx.coroutines.flow.StateFlow

public interface Stream {
	/**
	 * The last fetched [StreamInfo], or null if it has not been fetched yet.
	 * On creation of the Stream object, an initial fetch will be triggered, so this value will eventually be non-null.
	 */
	public val info: StateFlow<StreamInfo?>

	/**
	 * Requests the latest stream info. Future access to [info] will return this new updated value.
	 */
	public suspend fun updateStreamInfo(): Result<StreamInfo>
}

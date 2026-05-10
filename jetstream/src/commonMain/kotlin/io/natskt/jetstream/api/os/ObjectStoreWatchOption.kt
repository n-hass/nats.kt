package io.natskt.jetstream.api.os

/**
 * Options that customise the behaviour of [ObjectStoreBucket.watch]. The
 * defaults (no options) produce a watch that delivers the most recent
 * version of every object first, then continues with future updates,
 * including delete tombstones.
 */
public enum class ObjectStoreWatchOption {
	/**
	 * Skip emissions for objects that have been tombstoned.
	 */
	IgnoreDelete,

	/**
	 * Deliver every historical revision instead of just the latest per object
	 * (DeliverPolicy.All).
	 */
	IncludeHistory,

	/**
	 * Deliver only updates that occur after the watch starts (DeliverPolicy.New).
	 */
	UpdatesOnly,
}

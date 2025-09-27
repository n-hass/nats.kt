package io.natskt.nkeys

public enum class NKeyType(
	internal val prefix: Int,
) {
	Account(0 shl 3),
	Server(13 shl 3),
	Cluster(2 shl 3),
	User(20 shl 3),
	Operator(14 shl 3),
	;

	public companion object {
		private val lookup = entries.associateBy { it.prefix }

		public fun fromPrefix(prefix: Int): NKeyType? = lookup[prefix and 0xFF]
	}
}

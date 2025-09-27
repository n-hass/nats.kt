package io.natskt.nkeys

public object NKeys {
	public data class Creds(
		public val jwt: String?,
		public val seed: NKeySeed,
	)

	public fun parseSeed(seed: String): NKeySeed = NKeySeed.parse(seed)

	public fun parseCreds(content: String): Creds {
		val jwtBlock = extractBlock(content, "-----BEGIN NATS USER JWT-----", "-----END NATS USER JWT-----")
		val seedBlock =
			extractBlock(content, "-----BEGIN USER NKEY SEED-----", "-----END USER NKEY SEED-----")
				?: throw IllegalArgumentException("Credentials file did not contain a user NKEY seed block")

		val jwt = jwtBlock?.let(::compact)
		val seedValue = compact(seedBlock)
		return Creds(jwt, parseSeed(seedValue))
	}

	private fun extractBlock(
		content: String,
		beginMarker: String,
		endMarker: String,
	): String? {
		val lines = content.lineSequence().toList()
		val start = lines.indexOfFirst { it.contains(beginMarker, ignoreCase = true) }
		if (start == -1) return null
		val tail = lines.listIterator(start + 1)
		val blockLines = mutableListOf<String>()
		while (tail.hasNext()) {
			val line = tail.next()
			if (line.contains(endMarker, ignoreCase = true)) {
				return blockLines.joinToString(separator = "\n")
			}
			blockLines += line
		}
		throw IllegalArgumentException("Credentials block for $beginMarker missing terminator")
	}

	private fun compact(value: String): String =
		value
			.lines()
			.joinToString(separator = "") { it.trim() }
}

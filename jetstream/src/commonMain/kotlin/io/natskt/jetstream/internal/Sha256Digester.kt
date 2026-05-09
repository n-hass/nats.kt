package io.natskt.jetstream.internal

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.io.Buffer
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal const val SHA256_DIGEST_PREFIX: String = "SHA-256="

/**
 * Streams bytes into a SHA-256 digest computed by [finishDigestEntry].
 *
 * Bytes are appended to a segmented [kotlinx.io.Buffer]; at finalize time the
 * buffer is fed to the hasher as a [kotlinx.io.RawSource], so the hash function
 * pulls and drains 8KB segments instead of requiring one contiguous ByteArray
 * of the full payload to materialise.
 */
@OptIn(ExperimentalEncodingApi::class)
internal class Sha256Digester {
	private val buffer = Buffer()

	fun update(bytes: ByteArray) {
		if (bytes.isEmpty()) return
		buffer.write(bytes)
	}

	suspend fun finishDigestEntry(): String {
		val raw =
			CryptographyProvider.Default
				.get(SHA256)
				.hasher()
				.hash(buffer)
		return SHA256_DIGEST_PREFIX + Base64.UrlSafe.encode(raw.toByteArray())
	}
}

package io.natskt.jetstream.internal

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal const val SHA256_DIGEST_PREFIX: String = "SHA-256="

/**
 * Accumulates bytes for a SHA-256 digest computed by [finishDigestEntry].
 *
 * The whyoleg cryptography library only exposes a synchronous [HashFunction],
 * which the WebCrypto-backed JS/Wasm providers reject (`Only non-blocking
 * (suspend) calls are supported`). To stay multiplatform we buffer the input
 * and invoke the suspend one-shot [hash][dev.whyoleg.cryptography.operations.Hasher.hash]
 * exactly once at the end. Memory usage is bounded by the size of the object
 * being put or retrieved, which the caller already retains in the eager paths.
 */
@OptIn(ExperimentalEncodingApi::class)
internal class Sha256Digester {
	private val buffers = mutableListOf<ByteArray>()
	private var totalSize = 0

	fun update(bytes: ByteArray) {
		if (bytes.isEmpty()) return
		buffers += bytes
		totalSize += bytes.size
	}

	suspend fun finishDigestEntry(): String {
		val combined =
			when {
				buffers.isEmpty() -> ByteArray(0)
				buffers.size == 1 -> buffers[0]
				else -> {
					val out = ByteArray(totalSize)
					var offset = 0
					for (b in buffers) {
						b.copyInto(out, offset)
						offset += b.size
					}
					out
				}
			}
		val raw =
			CryptographyProvider.Default
				.get(SHA256)
				.hasher()
				.hash(combined)
		return SHA256_DIGEST_PREFIX + Base64.UrlSafe.encode(raw)
	}
}

package io.natskt.tls.internal

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.algorithms.SHA384

internal class TlsDigest : AutoCloseable {
	private val accumulated = mutableListOf<ByteArray>()
	private var totalSize = 0

	fun update(data: ByteArray) {
		if (data.isEmpty()) return
		accumulated.add(data)
		totalSize += data.size
	}

	fun addHandshakeMessage(message: TlsHandshakeMessage) {
		val header = buildHandshakeType(message.type, message.data.size)
		accumulated.add(header)
		accumulated.add(message.data)
		totalSize += header.size + message.data.size
	}

	fun doHash(algorithmId: dev.whyoleg.cryptography.CryptographyAlgorithmId<Digest>): ByteArray {
		val combined =
			if (accumulated.size == 1) {
				accumulated[0]
			} else {
				val buf = ByteArray(totalSize)
				var offset = 0
				for (chunk in accumulated) {
					chunk.copyInto(buf, offset)
					offset += chunk.size
				}
				buf
			}
		return CryptographyProvider.Default
			.get(algorithmId)
			.hasher()
			.hashBlocking(combined)
	}

	override fun close() {
		accumulated.clear()
		totalSize = 0
	}
}

internal fun digestAlgorithmForHash(hashCode: Int): dev.whyoleg.cryptography.CryptographyAlgorithmId<Digest> =
	when (hashCode) {
		4 -> SHA256
		5 -> SHA384
		else -> throw TlsException("Unsupported TLS hash algorithm: $hashCode")
	}

internal fun digestAlgorithmForSuite(suite: SuiteInfo): dev.whyoleg.cryptography.CryptographyAlgorithmId<Digest> =
	when (suite.hashAlgorithm) {
		5 -> SHA384
		else -> SHA256
	}

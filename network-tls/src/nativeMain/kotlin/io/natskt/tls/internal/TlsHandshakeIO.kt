package io.natskt.tls.internal

import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.readByteArray

internal fun Sink.writeTlsClientHello(
	suites: List<SuiteInfo>,
	clientRandom: ByteArray,
	serverName: String?,
	keySharePublicKey: ByteArray? = null,
	keyShareGroup: Short = 23,
) {
	// Legacy version: TLS 1.2 (required for TLS 1.3 compat)
	writeShort(TlsVersion.TLS12.code.toShort())
	write(clientRandom)

	// Session ID: empty
	writeByte(0)

	// Cipher suites (TLS 1.3 + TLS 1.2 combined)
	writeShort((suites.size * 2).toShort())
	for (suite in suites) {
		writeShort(suite.code)
	}

	// Compression: null only
	writeByte(1)
	writeByte(0)

	// Extensions
	val extensions = mutableListOf<ByteArray>()
	extensions += buildSignatureAlgorithmsExtension()
	extensions += buildECCurvesExtension()
	extensions += buildECPointFormatExtension()
	extensions += buildSupportedVersionsExtension()
	if (keySharePublicKey != null) {
		extensions += buildKeyShareExtension(keySharePublicKey, keyShareGroup)
	}
	if (serverName != null) {
		extensions += buildServerNameExtension(serverName)
	}

	writeShort(extensions.sumOf { it.size }.toShort())
	for (ext in extensions) {
		write(ext)
	}
}

internal fun Sink.writeECPoint(point: ByteArray) {
	writeByte(point.size.toByte())
	write(point)
}

internal fun Sink.writeShort(value: Short) {
	writeByte((value.toInt() shr 8).toByte())
	writeByte(value.toByte())
}

internal fun buildClientHelloBytes(
	suites: List<SuiteInfo>,
	clientRandom: ByteArray,
	serverName: String?,
	keySharePublicKey: ByteArray? = null,
	keyShareGroup: Short = 23,
): ByteArray {
	val buf = Buffer()
	buf.writeTlsClientHello(suites, clientRandom, serverName, keySharePublicKey, keyShareGroup)
	return buf.readByteArray()
}

// --- Extension builders ---

private fun buildSignatureAlgorithmsExtension(): ByteArray {
	val buf = Buffer()
	buf.writeShort(13) // SIGNATURE_ALGORITHMS
	val algorithms =
		listOf(
			byteArrayOf(5, 3),
			byteArrayOf(4, 3), // SHA384/SHA256 + ECDSA
			byteArrayOf(8, 4), // RSA-PSS with SHA256 (needed for TLS 1.3)
			byteArrayOf(8, 5), // RSA-PSS with SHA384
			byteArrayOf(8, 6), // RSA-PSS with SHA512
			byteArrayOf(6, 1),
			byteArrayOf(5, 1),
			byteArrayOf(4, 1),
			byteArrayOf(2, 1), // RSA
		)
	val size = algorithms.size
	buf.writeShort((2 + size * 2).toShort())
	buf.writeShort((size * 2).toShort())
	for (alg in algorithms) {
		buf.writeByte(alg[0])
		buf.writeByte(alg[1])
	}
	return buf.readByteArray()
}

private fun buildECCurvesExtension(): ByteArray {
	val buf = Buffer()
	buf.writeShort(10) // SUPPORTED_GROUPS / ELLIPTIC_CURVES
	val curves = listOf<Short>(23, 24) // secp256r1, secp384r1
	val size = curves.size * 2
	buf.writeShort((2 + size).toShort())
	buf.writeShort(size.toShort())
	for (curve in curves) {
		buf.writeShort(curve)
	}
	return buf.readByteArray()
}

private fun buildECPointFormatExtension(): ByteArray {
	val buf = Buffer()
	buf.writeShort(11) // EC_POINT_FORMAT
	val formats = listOf<Byte>(0, 1, 2)
	buf.writeShort((1 + formats.size).toShort())
	buf.writeByte(formats.size.toByte())
	for (f in formats) {
		buf.writeByte(f)
	}
	return buf.readByteArray()
}

private fun buildSupportedVersionsExtension(): ByteArray {
	val buf = Buffer()
	buf.writeShort(43) // SUPPORTED_VERSIONS extension type
	// Length: 1 (list length byte) + 2 versions * 2 bytes = 5
	buf.writeShort(5)
	buf.writeByte(4) // list length: 4 bytes
	buf.writeShort(TlsVersion.TLS13.code.toShort()) // 0x0304
	buf.writeShort(TlsVersion.TLS12.code.toShort()) // 0x0303
	return buf.readByteArray()
}

/**
 * key_share extension (type 51) with a single key share entry.
 * Format: extension_type(2) || length(2) || client_shares_length(2) || group(2) || key_length(2) || key
 */
internal fun buildKeyShareExtension(
	publicKey: ByteArray,
	group: Short = 23,
): ByteArray {
	val buf = Buffer()
	buf.writeShort(51) // KEY_SHARE extension type
	val shareLength = 2 + 2 + publicKey.size // group + key_length + key
	buf.writeShort((2 + shareLength).toShort()) // extension data length
	buf.writeShort(shareLength.toShort()) // client shares length
	buf.writeShort(group)
	buf.writeShort(publicKey.size.toShort()) // key length
	buf.write(publicKey)
	return buf.readByteArray()
}

private fun buildServerNameExtension(name: String): ByteArray {
	val buf = Buffer()
	buf.writeShort(0) // SERVER_NAME
	buf.writeShort((name.length + 5).toShort())
	buf.writeShort((name.length + 3).toShort())
	buf.writeByte(0)
	buf.writeShort(name.length.toShort())
	buf.write(name.encodeToByteArray())
	return buf.readByteArray()
}

private fun Buffer.writeShort(value: Short) {
	writeByte((value.toInt() shr 8).toByte())
	writeByte(value.toByte())
}

private fun Buffer.writeShort(value: Int) {
	writeByte((value shr 8).toByte())
	writeByte(value.toByte())
}

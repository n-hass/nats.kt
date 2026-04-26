package io.natskt.tls.internal

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readPacket
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeFully
import kotlinx.io.readByteArray

private const val MAX_TLS_FRAME_SIZE = 0x4800

internal suspend fun ByteReadChannel.readTlsRecord(): TlsRecord {
	val typeByte = readByte().toInt() and 0xff
	val type = TlsRecordType.byCode(typeByte)
	// RFC 8446 §5.1: legacy_record_version MUST be ignored for all purposes
	readByte() // version high
	readByte() // version low
	val lengthHigh = readByte().toInt() and 0xff
	val lengthLow = readByte().toInt() and 0xff
	val length = (lengthHigh shl 8) or lengthLow
	if (length > MAX_TLS_FRAME_SIZE) throw TlsException("Illegal TLS frame size: $length")
	val data = readPacket(length).readByteArray()
	return TlsRecord(type, data = data)
}

internal suspend fun ByteWriteChannel.writeRecordBytes(
	type: TlsRecordType,
	data: ByteArray,
	offset: Int = 0,
	length: Int = data.size - offset,
) {
	writeByte(type.code.toByte())
	writeByte(0x03.toByte())
	writeByte(0x03.toByte())
	writeByte((length shr 8).toByte())
	writeByte(length.toByte())
	writeFully(data, offset, offset + length)
	flush()
}

internal suspend fun ByteWriteChannel.writeRecord(record: TlsRecord) {
	writeRecordBytes(record.type, record.data, record.offset, record.length)
}

// --- Handshake message parsing (ByteArray-based) ---

internal class ByteArrayReader(
	val data: ByteArray,
	var pos: Int = 0,
) {
	val remaining: Int get() = data.size - pos

	fun readByte(): Int {
		check(pos < data.size) { "Unexpected end of TLS data" }
		return data[pos++].toInt() and 0xff
	}

	fun readShort(): Int = (readByte() shl 8) or readByte()

	fun readBytes(count: Int): ByteArray {
		check(pos + count <= data.size) { "Unexpected end of TLS data" }
		val result = data.copyOfRange(pos, pos + count)
		pos += count
		return result
	}

	fun skip(count: Int) {
		pos += count
	}

	fun exhausted(): Boolean = pos >= data.size
}

internal fun parseHandshakeMessages(data: ByteArray): List<TlsHandshakeMessage> {
	val reader = ByteArrayReader(data)
	val messages = mutableListOf<TlsHandshakeMessage>()
	while (!reader.exhausted()) {
		val typeCode = reader.readByte()
		val b1 = reader.readByte()
		val b2 = reader.readByte()
		val b3 = reader.readByte()
		val length = (b1 shl 16) or (b2 shl 8) or b3
		val body = reader.readBytes(length)
		messages += TlsHandshakeMessage(TlsHandshakeType.byCode(typeCode), body)
	}
	return messages
}

internal fun parseServerHello(data: ByteArray): TlsServerHello {
	val r = ByteArrayReader(data)
	val version = TlsVersion.byCode(r.readShort())
	val random = r.readBytes(32)
	val sessionIdLength = r.readByte()
	if (sessionIdLength > 32) throw TlsException("sessionId length limit exceeded: $sessionIdLength")
	val sessionId = ByteArray(32)
	if (sessionIdLength > 0) {
		r.readBytes(sessionIdLength).copyInto(sessionId)
	}
	val suite = r.readShort().toShort()
	val compressionMethod = r.readByte()
	if (compressionMethod != 0) throw TlsException("Unsupported compression method: $compressionMethod")
	if (r.exhausted()) return TlsServerHello(version, random, sessionId, suite)

	val extensionSize = r.readShort()
	val extensions = mutableListOf<TlsExtensionData>()
	val extensionEnd = r.pos + extensionSize
	while (r.pos < extensionEnd) {
		val extType = r.readShort()
		val extLength = r.readShort()
		val extData = r.readBytes(extLength)
		extensions += TlsExtensionData(extType, extData)
	}
	return TlsServerHello(version, random, sessionId, suite, extensions)
}

internal fun ByteArrayReader.readCurveParams(): CurveInfo {
	val type = readByte()
	when (ServerKeyExchangeType.byCode(type)) {
		ServerKeyExchangeType.NamedCurve -> {
			val curveId = readShort().toShort()
			return CurveInfo.fromCode(curveId) ?: throw TlsException("Unknown EC curve id: $curveId")
		}
		ServerKeyExchangeType.ExplicitPrime -> error("ExplicitPrime not supported")
		ServerKeyExchangeType.ExplicitChar -> error("ExplicitChar not supported")
	}
}

internal fun ByteArrayReader.readECPoint(fieldSize: Int): ByteArray {
	val pointSize = readByte()
	val pointData = readBytes(pointSize)
	if (pointData[0] != 4.toByte()) throw TlsException("Only uncompressed EC points supported")
	val componentLength = (pointSize - 1) / 2
	if ((fieldSize + 7) ushr 3 != componentLength) throw TlsException("Invalid EC point component length")
	return pointData
}

internal fun ByteArrayReader.readHashAndSign(): HashAndSignInfo? {
	val hash = readByte()
	val sign = readByte()
	return HashAndSignInfo.from(hash, sign)
}

internal enum class CurveInfo(
	val code: Short,
	val fieldSize: Int,
	val curveName: String,
) {
	Secp256r1(23, 256, "P-256"),
	Secp384r1(24, 384, "P-384"),
	Secp521r1(25, 521, "P-521"),
	;

	companion object {
		fun fromCode(code: Short): CurveInfo? = entries.find { it.code == code }
	}
}

internal class HashAndSignInfo(
	val hashCode: Int,
	val signCode: Int,
) {
	val isEcdsa: Boolean get() = signCode == 3
	val isRsa: Boolean get() = signCode == 1

	companion object {
		fun from(
			hash: Int,
			sign: Int,
		): HashAndSignInfo? {
			if (sign == 0) return null
			return HashAndSignInfo(hash, sign)
		}
	}
}

internal fun buildHandshakeType(
	type: TlsHandshakeType,
	length: Int,
): ByteArray {
	if (length > 0xffffff) throw TlsException("TLS handshake size limit exceeded: $length")
	return byteArrayOf(
		type.code.toByte(),
		(length shr 16).toByte(),
		(length shr 8).toByte(),
		length.toByte(),
	)
}

package io.natskt.tls.internal

internal class DerReader(
	private val data: ByteArray,
	private var pos: Int = 0,
) {
	val remaining: Int get() = data.size - pos

	fun readByte(): Int {
		if (pos >= data.size) throw TlsException("DER: unexpected end of data")
		return data[pos++].toInt() and 0xff
	}

	fun readBytes(count: Int): ByteArray {
		if (pos + count > data.size) throw TlsException("DER: unexpected end of data (need $count, have ${data.size - pos})")
		val result = data.copyOfRange(pos, pos + count)
		pos += count
		return result
	}

	fun readTag(): Int = readByte()

	fun readLength(): Int {
		val first = readByte()
		if (first < 0x80) return first
		val numBytes = first and 0x7f
		var length = 0
		for (i in 0 until numBytes) {
			length = (length shl 8) or readByte()
		}
		return length
	}

	fun readSequence(): DerReader {
		val tag = readTag()
		if (tag != 0x30) throw TlsException("DER: expected SEQUENCE (0x30), got 0x${tag.toString(16)}")
		val length = readLength()
		val content = readBytes(length)
		return DerReader(content)
	}

	fun readOid(): String {
		val tag = readTag()
		if (tag != 0x06) throw TlsException("DER: expected OID (0x06), got 0x${tag.toString(16)}")
		val length = readLength()
		val oidBytes = readBytes(length)
		if (oidBytes.isEmpty()) throw TlsException("DER: empty OID")
		return decodeOid(oidBytes)
	}

	fun readBitString(): ByteArray {
		val tag = readTag()
		if (tag != 0x03) throw TlsException("DER: expected BIT STRING (0x03), got 0x${tag.toString(16)}")
		val length = readLength()
		readByte() // unused bits
		return readBytes(length - 1)
	}

	fun skip() {
		readTag()
		val length = readLength()
		readBytes(length)
	}

	fun peekTag(): Int {
		if (pos >= data.size) throw TlsException("DER: unexpected end of data")
		return data[pos].toInt() and 0xff
	}

	fun snapshot(length: Int): ByteArray = data.copyOfRange(pos, pos + length)

	fun savePosition(): Int = pos

	fun restorePosition(saved: Int) {
		pos = saved
	}

	private fun decodeOid(bytes: ByteArray): String {
		val result = StringBuilder()
		val first = bytes[0].toInt() and 0xff
		result.append(first / 40)
		result.append('.')
		result.append(first % 40)

		var value = 0
		for (i in 1 until bytes.size) {
			val b = bytes[i].toInt() and 0xff
			value = (value shl 7) or (b and 0x7f)
			if (b and 0x80 == 0) {
				result.append('.')
				result.append(value)
				value = 0
			}
		}
		return result.toString()
	}
}

internal sealed class CertPublicKey {
	class Ec(
		val curveOid: String,
		val point: ByteArray,
	) : CertPublicKey()

	class Rsa(
		val spkiDer: ByteArray,
	) : CertPublicKey()
}

private const val OID_EC_PUBLIC_KEY = "1.2.840.10045.2.1"
private const val OID_RSA_ENCRYPTION = "1.2.840.113549.1.1.1"
internal const val OID_SECP256R1 = "1.2.840.10045.3.1.7"
internal const val OID_SECP384R1 = "1.3.132.0.34"
internal const val OID_SECP521R1 = "1.3.132.0.35"

internal fun extractPublicKeyFromCertificate(certDer: ByteArray): CertPublicKey {
	try {
		val cert = DerReader(certDer)
		val tbsCert = cert.readSequence()
		val tbs = tbsCert.readSequence()

		if (tbs.peekTag() == 0xa0) tbs.skip()
		tbs.skip() // serialNumber
		tbs.skip() // signature AlgorithmIdentifier
		tbs.skip() // issuer
		tbs.skip() // validity
		tbs.skip() // subject

		val spki = tbs.readSequence()
		val algorithm = spki.readSequence()
		val algorithmOid = algorithm.readOid()

		return when (algorithmOid) {
			OID_EC_PUBLIC_KEY -> {
				val curveOid = algorithm.readOid()
				val publicKeyBits = spki.readBitString()
				CertPublicKey.Ec(curveOid, publicKeyBits)
			}
			OID_RSA_ENCRYPTION -> {
				CertPublicKey.Rsa(extractSpkiFromCertDer(certDer))
			}
			else -> throw TlsException("Unsupported public key algorithm: $algorithmOid")
		}
	} catch (e: TlsException) {
		throw e
	} catch (e: Exception) {
		throw TlsException("Malformed X.509 certificate: ${e.message}", e)
	}
}

private fun extractSpkiFromCertDer(certDer: ByteArray): ByteArray {
	var pos = 0

	fun rd(): Int {
		if (pos >= certDer.size) throw TlsException("DER: unexpected end in SPKI extraction")
		return certDer[pos++].toInt() and 0xff
	}

	fun rdLen(): Int {
		val first = rd()
		if (first < 0x80) return first
		val n = first and 0x7f
		var len = 0
		repeat(n) { len = (len shl 8) or rd() }
		return len
	}

	fun skipTlv() {
		rd()
		val len = rdLen()
		pos += len
	}

	if (rd() != 0x30) throw TlsException("DER: expected Certificate SEQUENCE")
	rdLen()
	if (rd() != 0x30) throw TlsException("DER: expected TBSCertificate SEQUENCE")
	rdLen()
	if (certDer[pos].toInt() and 0xff == 0xa0) skipTlv()
	skipTlv() // serialNumber
	skipTlv() // signature
	skipTlv() // issuer
	skipTlv() // validity
	skipTlv() // subject
	val spkiStart = pos
	if (rd() != 0x30) throw TlsException("DER: expected SPKI SEQUENCE")
	val spkiContentLen = rdLen()
	return certDer.copyOfRange(spkiStart, pos + spkiContentLen)
}

internal fun parseCertificatesDer(data: ByteArray): List<ByteArray> {
	if (data.size < 3) throw TlsException("Certificate chain data too short")
	var pos = 0

	fun rd(): Int = data[pos++].toInt() and 0xff

	val chainLength = (rd() shl 16) or (rd() shl 8) or rd()
	val chainEnd = pos + chainLength
	if (chainEnd > data.size) throw TlsException("Certificate chain length exceeds data")
	val certs = mutableListOf<ByteArray>()
	while (pos < chainEnd) {
		val certLength = (rd() shl 16) or (rd() shl 8) or rd()
		if (pos + certLength > chainEnd) throw TlsException("Certificate length exceeds chain")
		certs.add(data.copyOfRange(pos, pos + certLength))
		pos += certLength
	}
	return certs
}

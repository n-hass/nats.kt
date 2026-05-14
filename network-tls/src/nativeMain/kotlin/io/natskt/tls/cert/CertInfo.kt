package io.natskt.tls.cert

import io.ktor.network.tls.TlsException
import io.natskt.tls.internal.DerReader

internal sealed class SubjectAltName {
	class Dns(
		val name: String,
	) : SubjectAltName()

	class Ip(
		val addr: ByteArray,
	) : SubjectAltName()
}

internal enum class SignatureAlgorithm(
	val oid: String,
) {
	EcdsaSha256("1.2.840.10045.4.3.2"),
	EcdsaSha384("1.2.840.10045.4.3.3"),
	EcdsaSha512("1.2.840.10045.4.3.4"),
	RsaSha256("1.2.840.113549.1.1.11"),
	RsaSha384("1.2.840.113549.1.1.12"),
	RsaSha512("1.2.840.113549.1.1.13"),
	;

	companion object {
		fun forOid(oid: String): SignatureAlgorithm? = entries.firstOrNull { it.oid == oid }
	}
}

/**
 * Parsed X.509 cert fields required for chain validation: the signed portion (TBS),
 * signature material, validity window, issuer/subject DNs for chain construction, and
 * the subject-alt-names (SAN) for hostname verification on the leaf.
 */
internal class CertInfo(
	val der: ByteArray,
	val tbsBytes: ByteArray,
	val signatureAlgOid: String,
	val signatureBytes: ByteArray,
	val subjectDer: ByteArray,
	val issuerDer: ByteArray,
	val notBeforeMillis: Long,
	val notAfterMillis: Long,
	val sans: List<SubjectAltName>,
) {
	val signatureAlgorithm: SignatureAlgorithm?
		get() = SignatureAlgorithm.forOid(signatureAlgOid)
}

private const val OID_SUBJECT_ALT_NAME = "2.5.29.17"

internal fun parseCertInfo(certDer: ByteArray): CertInfo {
	try {
		// Certificate ::= SEQUENCE { TBSCertificate, signatureAlgorithm, signatureValue BIT STRING }
		val outer = DerReader(certDer).readSequence()
		val tbsBytes = outer.readTlv()
		val sigAlg = outer.readSequence()
		val sigAlgOid = sigAlg.readOid()
		val signatureBytes = outer.readBitString()

		val tbs = DerReader(tbsBytes).readSequence()
		if (tbs.peekTag() == 0xa0) tbs.skip() // version [0] EXPLICIT
		tbs.skip() // serialNumber
		tbs.skip() // signature AlgorithmIdentifier (duplicate of outer; we read outer)

		val issuerDer = tbs.readTlv()

		val validity = tbs.readSequence()
		val notBefore = readTime(validity)
		val notAfter = readTime(validity)

		val subjectDer = tbs.readTlv()

		tbs.skip() // subjectPublicKeyInfo

		var sans = emptyList<SubjectAltName>()
		while (tbs.remaining > 0) {
			if (tbs.peekTag() == 0xa3) {
				// Extensions ::= [3] EXPLICIT SEQUENCE OF Extension
				tbs.readTag()
				tbs.readLength()
				val exts = tbs.readSequence()
				while (exts.remaining > 0) {
					val ext = exts.readSequence()
					val extOid = ext.readOid()
					if (ext.peekTag() == 0x01) ext.skip() // optional critical BOOLEAN
					val extValue = ext.readOctetString()
					if (extOid == OID_SUBJECT_ALT_NAME) sans = parseSans(extValue)
				}
			} else {
				tbs.skip()
			}
		}

		return CertInfo(
			der = certDer,
			tbsBytes = tbsBytes,
			signatureAlgOid = sigAlgOid,
			signatureBytes = signatureBytes,
			subjectDer = subjectDer,
			issuerDer = issuerDer,
			notBeforeMillis = notBefore,
			notAfterMillis = notAfter,
			sans = sans,
		)
	} catch (e: TlsException) {
		throw e
	} catch (e: Exception) {
		throw TlsException("Malformed X.509 certificate", e)
	}
}

private fun readTime(reader: DerReader): Long {
	val tag = reader.readTag()
	val length = reader.readLength()
	val bytes = reader.readBytes(length)
	val s = bytes.decodeToString()
	return when (tag) {
		0x17 -> parseUtcTime(s)
		0x18 -> parseGeneralizedTime(s)
		else -> throw TlsException("Unexpected Time tag: 0x${tag.toString(16)}")
	}
}

// UTCTime: "YYMMDDHHMMSSZ"
private fun parseUtcTime(s: String): Long {
	if (s.length != 13 || s.last() != 'Z') throw TlsException("Bad UTCTime: $s")
	val yy = s.substring(0, 2).toInt()
	val fullYear = if (yy >= 50) 1900 + yy else 2000 + yy
	return civilToMillis(
		fullYear,
		s.substring(2, 4).toInt(),
		s.substring(4, 6).toInt(),
		s.substring(6, 8).toInt(),
		s.substring(8, 10).toInt(),
		s.substring(10, 12).toInt(),
	)
}

// GeneralizedTime: "YYYYMMDDHHMMSSZ"
private fun parseGeneralizedTime(s: String): Long {
	if (s.length < 15 || s.last() != 'Z') throw TlsException("Bad GeneralizedTime: $s")
	return civilToMillis(
		s.substring(0, 4).toInt(),
		s.substring(4, 6).toInt(),
		s.substring(6, 8).toInt(),
		s.substring(8, 10).toInt(),
		s.substring(10, 12).toInt(),
		s.substring(12, 14).toInt(),
	)
}

private fun civilToMillis(
	year: Int,
	month: Int,
	day: Int,
	hour: Int,
	minute: Int,
	second: Int,
): Long {
	val days = daysFromCivil(year, month, day)
	return days * 86_400_000L + hour * 3_600_000L + minute * 60_000L + second * 1000L
}

// Howard Hinnant's days_from_civil — days from 1970-01-01 UTC.
// Implemented locally to avoid pulling in kotlinx-datetime for this single use.
private fun daysFromCivil(
	year: Int,
	month: Int,
	day: Int,
): Long {
	val y = if (month <= 2) year - 1 else year
	val era = (if (y >= 0) y else y - 399) / 400
	val yoe = (y - era * 400).toLong()
	val doy = (153 * (month + (if (month > 2) -3 else 9)) + 2) / 5 + day - 1
	val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
	return era.toLong() * 146_097L + doe - 719_468L
}

private fun parseSans(octetString: ByteArray): List<SubjectAltName> {
	val seq = DerReader(octetString).readSequence()
	val out = mutableListOf<SubjectAltName>()
	while (seq.remaining > 0) {
		val tag = seq.readTag()
		val length = seq.readLength()
		val value = seq.readBytes(length)
		when (tag) {
			0x82 -> out += SubjectAltName.Dns(value.decodeToString())
			0x87 -> out += SubjectAltName.Ip(value)
			else -> { /* skip rfc822Name, uri, etc. */ }
		}
	}
	return out
}

internal fun verifyHostname(
	hostname: String,
	sans: List<SubjectAltName>,
) {
	val isIp = hostname.isIpLiteral()
	val ipBytes =
		if (isIp) {
			hostname.parseIpv4Literal()
				?: throw TlsException("IPv6 SAN matching not yet supported: $hostname")
		} else {
			null
		}

	for (san in sans) {
		when (san) {
			is SubjectAltName.Dns -> if (!isIp && matchDnsName(hostname, san.name)) return
			is SubjectAltName.Ip -> if (ipBytes != null && san.addr.contentEquals(ipBytes)) return
		}
	}
	throw TlsException("Certificate hostname mismatch: $hostname not in SAN")
}

internal fun String.isIpLiteral(): Boolean {
	if (':' in this) return true
	val parts = split('.')
	return parts.size == 4 &&
		parts.all { p ->
			p.isNotEmpty() && p.length <= 3 && p.all { it.isDigit() } && p.toInt() in 0..255
		}
}

private fun String.parseIpv4Literal(): ByteArray? {
	if (':' in this) return null
	val parts = split('.')
	if (parts.size != 4) return null
	return ByteArray(4) { i -> parts[i].toInt().toByte() }
}

// RFC 6125 §6.4.3: leftmost-only wildcard matching for DNS SANs (no embedded wildcards).
private fun matchDnsName(
	hostname: String,
	pattern: String,
): Boolean {
	val h = hostname.lowercase()
	val p = pattern.lowercase()
	if ('*' !in p) return h == p
	val dot = p.indexOf('.')
	if (dot <= 0 || p.substring(0, dot) != "*") return false
	val patternTail = p.substring(dot)
	val hostDot = h.indexOf('.')
	if (hostDot <= 0) return false
	return h.substring(hostDot) == patternTail
}

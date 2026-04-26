package io.natskt.tls.internal

internal enum class CipherAlgorithm {
	AesGcm,
	ChaCha20Poly1305,
}

internal class SuiteInfo(
	val code: Short,
	val name: String,
	val exchangeType: ExchangeType,
	val keyStrengthBytes: Int,
	val fixedIvLength: Int,
	val ivLength: Int,
	val cipherTagBytes: Int,
	val hashAlgorithm: Int,
	val signatureAlgorithm: Int,
	val tls13: Boolean = false,
	val cipherAlgorithm: CipherAlgorithm = CipherAlgorithm.AesGcm,
) {
	val cipherTagBits: Int get() = cipherTagBytes * 8
	val macStrengthBytes: Int = 0
}

internal enum class ExchangeType {
	ECDHE,
	RSA,
}

// TLS 1.3 cipher suites
internal val Tls13Suites: List<SuiteInfo> =
	listOf(
		SuiteInfo(
			code = 0x1301,
			name = "TLS_AES_128_GCM_SHA256",
			exchangeType = ExchangeType.ECDHE,
			keyStrengthBytes = 16,
			fixedIvLength = 12,
			ivLength = 12,
			cipherTagBytes = 16,
			hashAlgorithm = 4,
			signatureAlgorithm = 0,
			tls13 = true,
			cipherAlgorithm = CipherAlgorithm.AesGcm,
		),
		SuiteInfo(
			code = 0x1302,
			name = "TLS_AES_256_GCM_SHA384",
			exchangeType = ExchangeType.ECDHE,
			keyStrengthBytes = 32,
			fixedIvLength = 12,
			ivLength = 12,
			cipherTagBytes = 16,
			hashAlgorithm = 5,
			signatureAlgorithm = 0,
			tls13 = true,
			cipherAlgorithm = CipherAlgorithm.AesGcm,
		),
		SuiteInfo(
			code = 0x1303,
			name = "TLS_CHACHA20_POLY1305_SHA256",
			exchangeType = ExchangeType.ECDHE,
			keyStrengthBytes = 32,
			fixedIvLength = 12,
			ivLength = 12,
			cipherTagBytes = 16,
			hashAlgorithm = 4,
			signatureAlgorithm = 0,
			tls13 = true,
			cipherAlgorithm = CipherAlgorithm.ChaCha20Poly1305,
		),
	)

// TLS 1.2 cipher suites
internal val Tls12Suites: List<SuiteInfo> =
	listOf(
		SuiteInfo(
			code = 0xc02c.toShort(),
			name = "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
			exchangeType = ExchangeType.ECDHE,
			keyStrengthBytes = 32,
			fixedIvLength = 4,
			ivLength = 12,
			cipherTagBytes = 16,
			hashAlgorithm = 5,
			signatureAlgorithm = 3,
		),
		SuiteInfo(
			code = 0xc030.toShort(),
			name = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
			exchangeType = ExchangeType.ECDHE,
			keyStrengthBytes = 32,
			fixedIvLength = 4,
			ivLength = 12,
			cipherTagBytes = 16,
			hashAlgorithm = 5,
			signatureAlgorithm = 1,
		),
		SuiteInfo(
			code = 0xc02b.toShort(),
			name = "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
			exchangeType = ExchangeType.ECDHE,
			keyStrengthBytes = 16,
			fixedIvLength = 4,
			ivLength = 12,
			cipherTagBytes = 16,
			hashAlgorithm = 4,
			signatureAlgorithm = 3,
		),
		SuiteInfo(
			code = 0xc02f.toShort(),
			name = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
			exchangeType = ExchangeType.ECDHE,
			keyStrengthBytes = 16,
			fixedIvLength = 4,
			ivLength = 12,
			cipherTagBytes = 16,
			hashAlgorithm = 4,
			signatureAlgorithm = 1,
		),
	)

internal val SupportedSuites: List<SuiteInfo> = Tls13Suites + Tls12Suites

internal fun findSuiteByCode(code: Short): SuiteInfo? = SupportedSuites.find { it.code == code }

internal fun findTls13SuiteByCode(code: Short): SuiteInfo? = Tls13Suites.find { it.code == code }

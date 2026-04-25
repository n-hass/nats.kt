package io.natskt.tls.internal

public class TlsException(
	message: String,
	cause: Throwable? = null,
) : Exception(message, cause)

internal enum class TlsRecordType(
	val code: Int,
) {
	ChangeCipherSpec(0x14),
	Alert(0x15),
	Handshake(0x16),
	ApplicationData(0x17),
	;

	companion object {
		fun byCode(code: Int): TlsRecordType = entries.find { it.code == code } ?: throw TlsException("Unknown TLS record type: $code")
	}
}

internal enum class TlsVersion(
	val code: Int,
) {
	TLS12(0x0303),
	TLS13(0x0304),
	;

	companion object {
		fun byCode(code: Int): TlsVersion = entries.find { it.code == code } ?: throw TlsException("Unknown TLS version: 0x${code.toString(16)}")
	}
}

internal enum class TlsHandshakeType(
	val code: Int,
) {
	HelloRequest(0),
	ClientHello(1),
	ServerHello(2),
	NewSessionTicket(4),
	EncryptedExtensions(8),
	Certificate(11),
	ServerKeyExchange(12),
	CertificateRequest(13),
	ServerDone(14),
	CertificateVerify(15),
	ClientKeyExchange(16),
	Finished(20),
	MessageHash(254),
	;

	companion object {
		fun byCode(code: Int): TlsHandshakeType = entries.find { it.code == code } ?: throw TlsException("Unknown TLS handshake type: $code")
	}
}

internal enum class TlsAlertLevel(
	val code: Int,
) {
	WARNING(1),
	FATAL(2),
	;

	companion object {
		fun byCode(code: Int): TlsAlertLevel = entries.find { it.code == code } ?: TlsAlertLevel.FATAL
	}
}

internal enum class TlsAlertType(
	val code: Int,
) {
	CloseNotify(0),
	UnexpectedMessage(10),
	BadRecordMac(20),
	DecryptionFailed(21),
	RecordOverflow(22),
	HandshakeFailure(40),
	BadCertificate(42),
	CertificateRevoked(44),
	CertificateExpired(45),
	CertificateUnknown(46),
	IllegalParameter(47),
	UnknownCa(48),
	DecodeError(50),
	DecryptError(51),
	ProtocolVersion(70),
	InternalError(80),
	;

	companion object {
		fun byCode(code: Int): TlsAlertType = entries.find { it.code == code } ?: TlsAlertType.InternalError
	}
}

internal enum class ServerKeyExchangeType(
	val code: Int,
) {
	ExplicitPrime(1),
	ExplicitChar(2),
	NamedCurve(3),
	;

	companion object {
		fun byCode(code: Int): ServerKeyExchangeType = entries.find { it.code == code } ?: throw TlsException("Unknown server key exchange type: $code")
	}
}

internal class TlsRecord(
	val type: TlsRecordType,
	val version: TlsVersion = TlsVersion.TLS12,
	val data: ByteArray,
	val offset: Int = 0,
	val length: Int = data.size - offset,
)

internal class TlsHandshakeMessage(
	val type: TlsHandshakeType,
	val data: ByteArray,
)

internal class TlsServerHello(
	val version: TlsVersion,
	val serverRandom: ByteArray,
	val sessionId: ByteArray,
	val cipherSuiteCode: Short,
	val extensions: List<TlsExtensionData> = emptyList(),
)

internal class TlsExtensionData(
	val type: Int,
	val data: ByteArray,
)

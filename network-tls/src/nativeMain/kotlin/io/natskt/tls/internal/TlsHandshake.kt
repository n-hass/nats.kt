@file:OptIn(
	dev.whyoleg.cryptography.DelicateCryptographyApi::class,
	kotlinx.cinterop.ExperimentalForeignApi::class,
)

package io.natskt.tls.internal

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.Digest
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.natskt.tls.cert.validateCertificateChain
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlin.coroutines.CoroutineContext

private const val EXT_SUPPORTED_VERSIONS = 43
private const val EXT_KEY_SHARE = 51
private const val EXT_COOKIE = 44
private const val EXT_EXTENDED_MASTER_SECRET = 0x0017

/**
 * TLS handshake and encrypted I/O.
 *
 * During the handshake, records are read/written synchronously from [rawInput]/[rawOutput].
 * After the handshake, two coroutine loops run for app data:
 * - Input parser: decrypts incoming records and writes plaintext to [appDataInput]
 * - Output encoder: reads from [appDataOutput], encrypts, and writes to [rawOutput]
 */
internal class TlsHandshake(
	private val rawInput: ByteReadChannel,
	private val rawOutput: ByteWriteChannel,
	private val serverName: String?,
	override val coroutineContext: CoroutineContext,
	private val verifyCertificates: Boolean = true,
) : CoroutineScope {
	private var digest = TlsDigest()
	private val clientRandom: ByteArray = generateClientRandom()
	private val legacySessionId: ByteArray = ByteArray(32).also { CryptographyRandom.nextBytes(it) }

	private val ecdh = CryptographyProvider.Default.get(ECDH)
	private var ecdhCurve = EC.Curve.P256
	private var ecdhGroupId: Short = 23
	private var ecdhKeyPair = ecdh.keyPairGenerator(ecdhCurve).generateKeyBlocking()
	private var ecdhPublicBytes = ecdhKeyPair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW)

	private lateinit var negotiatedSuite: SuiteInfo
	private var isTls13 = false
	private var useExtendedMasterSecret = false

	// Cipher used after handshake for app data
	private lateinit var tls12Cipher: GcmTlsCipher
	private lateinit var tls13Cipher: Tls13Cipher

	// Reassembly buffer for handshake messages that may span multiple records
	private val handshakeBuffer = HandshakeBuffer()

	// App-facing channels
	private val appInput = ByteChannel(autoFlush = true)
	private val appOutput = ByteChannel(autoFlush = true)
	val appDataInput: ByteReadChannel get() = appInput
	val appDataOutput: ByteWriteChannel get() = appOutput

	private var inputJob: kotlinx.coroutines.Job? = null
	private var outputJob: kotlinx.coroutines.Job? = null

	// --- Handshake: all I/O is synchronous (no coroutine producers) ---

	suspend fun negotiate() {
		try {
			val serverHello = performHandshake()
			startAppDataIO()
		} catch (cause: Throwable) {
			close()
			throw cause
		}
	}

	private suspend fun performHandshake(): TlsServerHello {
		// Send initial ClientHello
		val ch1Record = sendClientHello()

		// Read ServerHello (may be HelloRetryRequest)
		var (serverHello, serverHelloMsg) = receiveAndParseServerHello()

		// RFC 8446 §4.1.4: HelloRetryRequest
		if (isTls13 && isHelloRetryRequest(serverHello)) {
			handleHelloRetryRequest(serverHello, ch1Record, serverHelloMsg)
			serverHello = receiveAndParseServerHello().first
			if (isHelloRetryRequest(serverHello)) {
				throw TlsException("Server sent second HelloRetryRequest")
			}
		}

		if (!isTls13) {
			checkDowngradeProtection(serverHello.serverRandom)
		}

		if (isTls13) {
			negotiateTls13(serverHello)
		} else {
			negotiateTls12(serverHello)
		}

		return serverHello
	}

	private suspend fun sendClientHello(cookie: ByteArray? = null): ByteArray {
		val body = buildClientHelloBytes(SupportedSuites, clientRandom, serverName, ecdhPublicBytes, ecdhGroupId, legacySessionId, cookie)
		val record = buildHandshakeType(TlsHandshakeType.ClientHello, body.size) + body
		digest.update(record)
		rawOutput.writeRecordBytes(TlsRecordType.Handshake, record)
		return record
	}

	private suspend fun receiveAndParseServerHello(): Pair<TlsServerHello, TlsHandshakeMessage> {
		val msg = readHandshakeMessage(TlsHandshakeType.ServerHello)
		val serverHello = parseServerHello(msg.data)

		val svExt = serverHello.extensions.find { it.type == EXT_SUPPORTED_VERSIONS }
		if (svExt != null && svExt.data.size >= 2) {
			val version = ((svExt.data[0].toInt() and 0xff) shl 8) or (svExt.data[1].toInt() and 0xff)
			if (version == TlsVersion.TLS13.code) isTls13 = true
		}

		negotiatedSuite = findSuiteByCode(serverHello.cipherSuiteCode)
			?: throw TlsException("Unsupported cipher suite: 0x${serverHello.cipherSuiteCode.toString(16)}")

		// RFC 7627: check if server supports extended master secret (TLS 1.2 only)
		if (!isTls13) {
			useExtendedMasterSecret = serverHello.extensions.any { it.type == EXT_EXTENDED_MASTER_SECRET }
		}

		return serverHello to msg
	}

	/**
	 * RFC 8446 §4.1.4: HelloRetryRequest is a ServerHello with server_random
	 * equal to SHA-256("HelloRetryRequest").
	 */
	private fun isHelloRetryRequest(serverHello: TlsServerHello): Boolean = serverHello.serverRandom.contentEquals(HRR_RANDOM)

	/**
	 * RFC 8446 §4.1.4: Handle HelloRetryRequest.
	 * Replaces the transcript with a synthetic message_hash, regenerates the
	 * ECDH key pair for the server's requested group, and sends a new ClientHello.
	 */
	private suspend fun handleHelloRetryRequest(
		hrr: TlsServerHello,
		ch1Record: ByteArray,
		hrrMsg: TlsHandshakeMessage,
	) {
		// Extract the server's requested group from key_share extension
		val ksExt =
			hrr.extensions.find { it.type == EXT_KEY_SHARE }
				?: throw TlsException("HelloRetryRequest missing key_share extension")
		val requestedGroup = ((ksExt.data[0].toInt() and 0xff) shl 8) or (ksExt.data[1].toInt() and 0xff)

		// RFC 8446 §4.2.2: echo cookie from HRR if present
		val cookieExt = hrr.extensions.find { it.type == EXT_COOKIE }
		val cookie =
			if (cookieExt != null && cookieExt.data.size >= 3) {
				val r = ByteArrayReader(cookieExt.data)
				val cookieLen = r.readShort()
				if (cookieLen != r.remaining) throw TlsException("HelloRetryRequest cookie length mismatch")
				r.readBytes(cookieLen)
			} else {
				null
			}

		// RFC 8446 §4.1.4: selected_group must correspond to a group offered in supported_groups
		val newCurve =
			when (requestedGroup.toShort()) {
				CurveInfo.Secp256r1.code -> EC.Curve.P256
				CurveInfo.Secp384r1.code -> EC.Curve.P384
				else -> throw TlsException("HelloRetryRequest requested group not in supported_groups: 0x${requestedGroup.toString(16)}")
			}

		// RFC 8446 §4.4.1: replace transcript with synthetic message_hash.
		// Transcript-Hash(CH1, HRR, ... Mn) = Hash(message_hash || HRR || ... || Mn)
		// where message_hash is a handshake message of type 254 with body = Hash(CH1).
		val hashAlg = digestAlgorithmForSuite(negotiatedSuite)
		val ch1Hash =
			CryptographyProvider.Default
				.get(hashAlg)
				.hasher()
				.hashBlocking(ch1Record)
		digest.close()
		digest = TlsDigest()
		val syntheticRecord = buildHandshakeType(TlsHandshakeType.MessageHash, ch1Hash.size) + ch1Hash
		digest.update(syntheticRecord)
		// Re-add HRR to the transcript
		digest.addHandshakeMessage(hrrMsg)

		// Regenerate ECDH key pair for the requested group
		ecdhCurve = newCurve
		ecdhGroupId = requestedGroup.toShort()
		ecdhKeyPair = ecdh.keyPairGenerator(ecdhCurve).generateKeyBlocking()
		ecdhPublicBytes = ecdhKeyPair.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW)

		// Send new ClientHello with cookie (added to the new digest by sendClientHello)
		sendClientHello(cookie)

		// Note: CCS for middlebox compatibility is sent in negotiateTls13 before the client's
		// encrypted flight, not here. The HRR flow does not need a separate CCS after CH2.
	}

	/**
	 * Read the next handshake message. Handles both coalescing (multiple messages in one
	 * record) and fragmentation (one message split across records) per RFC 5246 §6.2.1.
	 */
	private suspend fun readHandshakeMessage(expectedType: TlsHandshakeType? = null): TlsHandshakeMessage {
		while (true) {
			val msg = handshakeBuffer.next()
			if (msg != null) {
				if (expectedType != null && msg.type != expectedType) {
					throw TlsException("Expected $expectedType, got ${msg.type}")
				}
				digest.addHandshakeMessage(msg)
				return msg
			}

			val record = rawInput.readTlsRecord()
			when (record.type) {
				TlsRecordType.ChangeCipherSpec -> continue
				TlsRecordType.Alert -> {
					val code = if (record.length >= 2) TlsAlertType.byCode(record.data[record.offset + 1].toInt() and 0xff) else TlsAlertType.InternalError
					throw TlsException("Alert during handshake: $code")
				}
				TlsRecordType.Handshake -> handshakeBuffer.append(record.data)
				else -> throw TlsException("Unexpected record type during handshake: ${record.type}")
			}
		}
	}

	// --- Post-handshake app data I/O ---

	private fun startAppDataIO() {
		inputJob =
			launch(CoroutineName("natskt-tls-input")) {
				try {
					while (true) {
						val rawRecord = rawInput.readTlsRecord()
						if (isTls13) {
							if (rawRecord.type == TlsRecordType.ChangeCipherSpec) continue
							val decrypted = tls13Cipher.decrypt(rawRecord.data, rawRecord.offset, rawRecord.length)
							when (decrypted.innerType) {
								TlsRecordType.ApplicationData -> {
									appInput.writeFully(decrypted.data)
									appInput.flush()
								}
								TlsRecordType.Handshake -> {} // NewSessionTicket -- ignore
								TlsRecordType.Alert -> {
									if (decrypted.data.size >= 2) {
										val alertType = TlsAlertType.byCode(decrypted.data[1].toInt() and 0xff)
										if (alertType == TlsAlertType.CloseNotify) return@launch
										// RFC 8446 §6: all alerts other than close_notify are fatal in TLS 1.3
										throw TlsException("TLS 1.3 alert from server: $alertType")
									}
								}
								else -> {}
							}
						} else {
							when (rawRecord.type) {
								TlsRecordType.ApplicationData -> {
									val plaintext = tls12Cipher.decrypt(rawRecord.data, rawRecord.offset, rawRecord.length, rawRecord.type)
									appInput.writeFully(plaintext)
									appInput.flush()
								}
								TlsRecordType.Alert -> {
									val plaintext = tls12Cipher.decrypt(rawRecord.data, rawRecord.offset, rawRecord.length, rawRecord.type)
									if (plaintext.size >= 2) {
										val level = TlsAlertLevel.byCode(plaintext[0].toInt() and 0xff)
										val alertType = TlsAlertType.byCode(plaintext[1].toInt() and 0xff)
										if (alertType == TlsAlertType.CloseNotify) return@launch
										if (level == TlsAlertLevel.FATAL) throw TlsException("Fatal alert from server: $alertType")
									}
								}
								else -> {}
							}
						}
					}
				} catch (cause: io.ktor.utils.io.ClosedByteChannelException) {
					// Transport closed — treat as connection close, not error
				} catch (cause: kotlinx.io.EOFException) {
					// Transport EOF — treat as connection close, not error
				} catch (cause: Throwable) {
					appInput.cancel(cause)
				} finally {
					appInput.close()
				}
			}

		outputJob =
			launch(CoroutineName("natskt-tls-output")) {
				val buffer = ByteArray(16384)
				try {
					while (true) {
						val rc = appOutput.readAvailable(buffer)
						if (rc == -1) break
						if (isTls13) {
							val encrypted = tls13Cipher.encrypt(buffer, 0, rc, TlsRecordType.ApplicationData)
							rawOutput.writeRecordBytes(TlsRecordType.ApplicationData, encrypted)
						} else {
							val encrypted = tls12Cipher.encrypt(buffer, 0, rc, TlsRecordType.ApplicationData)
							rawOutput.writeRecordBytes(TlsRecordType.ApplicationData, encrypted)
						}
					}
				} catch (_: ClosedSendChannelException) {
				} catch (cause: Throwable) {
					appOutput.cancel(cause)
				}
			}
	}

	suspend fun closeGracefully() {
		try {
			val alert = byteArrayOf(TlsAlertLevel.WARNING.code.toByte(), TlsAlertType.CloseNotify.code.toByte())
			if (isTls13 && ::tls13Cipher.isInitialized) {
				val encrypted = tls13Cipher.encrypt(alert, 0, alert.size, TlsRecordType.Alert)
				rawOutput.writeRecordBytes(TlsRecordType.ApplicationData, encrypted)
			} else if (::tls12Cipher.isInitialized) {
				val encrypted = tls12Cipher.encrypt(alert, 0, alert.size, TlsRecordType.Alert)
				rawOutput.writeRecordBytes(TlsRecordType.Alert, encrypted)
			} else {
				rawOutput.writeRecordBytes(TlsRecordType.Alert, alert)
			}
		} catch (_: Throwable) {
			// Connection already broken — hard close
		}
		close()
	}

	fun close() {
		inputJob?.cancel()
		outputJob?.cancel()
		appInput.close()
		appOutput.close()
		digest.close()
		clientRandom.fill(0)
	}

	// ==================== TLS 1.3 ====================

	private suspend fun negotiateTls13(serverHello: TlsServerHello) {
		val ksExt =
			serverHello.extensions.find { it.type == EXT_KEY_SHARE }
				?: throw TlsException("TLS 1.3: missing key_share")
		val ksReader = ByteArrayReader(ksExt.data)
		val group = ksReader.readShort()
		if (group.toShort() != ecdhGroupId) {
			throw TlsException("TLS 1.3: server selected key share group 0x${group.toString(16)} but client offered 0x${ecdhGroupId.toString(16)}")
		}
		val keyLen = ksReader.readShort()
		val serverPubBytes = ksReader.readBytes(keyLen)

		val serverKeyCurve =
			when (group.toShort()) {
				CurveInfo.Secp256r1.code -> EC.Curve.P256
				CurveInfo.Secp384r1.code -> EC.Curve.P384
				CurveInfo.Secp521r1.code -> EC.Curve.P521
				else -> throw TlsException("TLS 1.3: unsupported key share group: $group")
			}
		val serverPubKey = ecdh.publicKeyDecoder(serverKeyCurve).decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, serverPubBytes)
		val sharedSecret = ecdhKeyPair.privateKey.sharedSecretGenerator().generateSharedSecretToByteArrayBlocking(serverPubKey)

		val hashAlg = digestAlgorithmForSuite(negotiatedSuite)
		val ks = Tls13KeySchedule(hashAlg)

		val helloHash = digest.doHash(hashAlg)
		val hsSecrets = ks.computeHandshakeSecrets(sharedSecret, helloHash)
		sharedSecret.fill(0)

		val serverHsKeys = ks.deriveTrafficKeys(hsSecrets.serverHandshakeTrafficSecret, negotiatedSuite.keyStrengthBytes, negotiatedSuite.ivLength)
		val clientHsKeys = ks.deriveTrafficKeys(hsSecrets.clientHandshakeTrafficSecret, negotiatedSuite.keyStrengthBytes, negotiatedSuite.ivLength)

		val hsCipher =
			when (negotiatedSuite.cipherAlgorithm) {
				CipherAlgorithm.AesGcm -> Tls13Cipher.createAesGcm(clientHsKeys.key, serverHsKeys.key, clientHsKeys.iv, serverHsKeys.iv)
				CipherAlgorithm.ChaCha20Poly1305 -> Tls13Cipher.createChaCha20Poly1305(clientHsKeys.key, serverHsKeys.key, clientHsKeys.iv, serverHsKeys.iv)
			}
		clientHsKeys.key.fill(0)
		clientHsKeys.iv.fill(0)
		serverHsKeys.key.fill(0)
		serverHsKeys.iv.fill(0)

		// Read encrypted handshake messages (with reassembly across records)
		var serverPublicKey: CertPublicKey? = null
		var certRequested = false
		val encHsBuffer = HandshakeBuffer()

		loop@ while (true) {
			val msg = encHsBuffer.next()
			if (msg != null) {
				when (msg.type) {
					TlsHandshakeType.EncryptedExtensions -> digest.addHandshakeMessage(msg)
					TlsHandshakeType.Certificate -> {
						digest.addHandshakeMessage(msg)
						val certChain = parseTls13CertificateChain(msg.data)
						if (verifyCertificates) validateCertificateChain(certChain, serverName)
						serverPublicKey = extractPublicKeyFromCertificate(certChain.first())
					}
					TlsHandshakeType.CertificateRequest -> {
						digest.addHandshakeMessage(msg)
						certRequested = true
					}
					TlsHandshakeType.CertificateVerify -> {
						verifyTls13CertificateVerify(msg.data, serverPublicKey ?: throw TlsException("Server certificate required but not received"), hashAlg)
						digest.addHandshakeMessage(msg)
					}
					TlsHandshakeType.Finished -> {
						verifyTls13ServerFinished(msg.data, hsSecrets.serverHandshakeTrafficSecret, hashAlg, ks)
						digest.addHandshakeMessage(msg)
						break@loop
					}
					else -> throw TlsException("TLS 1.3: unexpected: ${msg.type}")
				}
				continue@loop
			}

			val rawRecord = rawInput.readTlsRecord()
			if (rawRecord.type == TlsRecordType.ChangeCipherSpec) continue // middlebox compat
			if (rawRecord.type != TlsRecordType.ApplicationData) {
				throw TlsException("TLS 1.3: expected encrypted record, got ${rawRecord.type}")
			}

			val decrypted = hsCipher.decrypt(rawRecord.data, rawRecord.offset, rawRecord.length)
			if (decrypted.innerType != TlsRecordType.Handshake) {
				throw TlsException("TLS 1.3: expected handshake, got ${decrypted.innerType}")
			}
			encHsBuffer.append(decrypted.data)
		}

		// Derive application keys (transcript: CH..server Finished, before client Certificate)
		val appHash = digest.doHash(hashAlg)
		val appSecrets = ks.computeApplicationSecrets(hsSecrets.handshakeSecret, appHash)

		// Middlebox compatibility CCS (RFC 8446 §D.4): must precede client's encrypted flight
		rawOutput.writeRecordBytes(TlsRecordType.ChangeCipherSpec, byteArrayOf(1))

		// RFC 8446 §4.4.2: respond to CertificateRequest with empty Certificate
		if (certRequested) {
			// TLS 1.3 Certificate: context_len(1 byte: 0) + cert_list_len(3 bytes: 0,0,0)
			val emptyCertBody = byteArrayOf(0, 0, 0, 0)
			val certRecord = buildHandshakeType(TlsHandshakeType.Certificate, emptyCertBody.size) + emptyCertBody
			digest.update(certRecord)
			val encCert = hsCipher.encrypt(certRecord, 0, certRecord.size, TlsRecordType.Handshake)
			rawOutput.writeRecordBytes(TlsRecordType.ApplicationData, encCert)
		}

		// Send client Finished (transcript includes client Certificate if sent)
		val finHash = digest.doHash(hashAlg)
		val clientFinVerify = ks.finishedVerifyData(hsSecrets.clientHandshakeTrafficSecret, finHash)
		val finRecord = buildHandshakeType(TlsHandshakeType.Finished, clientFinVerify.size) + clientFinVerify
		val encFin = hsCipher.encrypt(finRecord, 0, finRecord.size, TlsRecordType.Handshake)
		rawOutput.writeRecordBytes(TlsRecordType.ApplicationData, encFin)

		// Create application cipher
		val sak = ks.deriveTrafficKeys(appSecrets.serverAppTrafficSecret, negotiatedSuite.keyStrengthBytes, negotiatedSuite.ivLength)
		val cak = ks.deriveTrafficKeys(appSecrets.clientAppTrafficSecret, negotiatedSuite.keyStrengthBytes, negotiatedSuite.ivLength)
		tls13Cipher =
			when (negotiatedSuite.cipherAlgorithm) {
				CipherAlgorithm.AesGcm -> Tls13Cipher.createAesGcm(cak.key, sak.key, cak.iv, sak.iv)
				CipherAlgorithm.ChaCha20Poly1305 -> Tls13Cipher.createChaCha20Poly1305(cak.key, sak.key, cak.iv, sak.iv)
			}

		// Wipe derivation intermediates
		cak.key.fill(0)
		cak.iv.fill(0)
		sak.key.fill(0)
		sak.iv.fill(0)
		appSecrets.clientAppTrafficSecret.fill(0)
		appSecrets.serverAppTrafficSecret.fill(0)
		hsSecrets.handshakeSecret.fill(0)
		hsSecrets.clientHandshakeTrafficSecret.fill(0)
		hsSecrets.serverHandshakeTrafficSecret.fill(0)
	}

	private fun parseTls13CertificateChain(data: ByteArray): List<ByteArray> {
		val r = ByteArrayReader(data)
		val ctxLen = r.readByte()
		if (ctxLen > 0) r.readBytes(ctxLen)
		val certsLen = (r.readByte() shl 16) or r.readShort()
		val certs = mutableListOf<ByteArray>()
		val end = r.pos + certsLen
		while (r.pos < end) {
			val certLen = (r.readByte() shl 16) or r.readShort()
			certs += r.readBytes(certLen)
			val extLen = r.readShort()
			if (extLen > 0) r.readBytes(extLen)
		}
		if (certs.isEmpty()) throw TlsException("TLS 1.3: no certificate")
		return certs
	}

	private fun verifyTls13CertificateVerify(
		data: ByteArray,
		serverPubKey: CertPublicKey,
		hashAlg: dev.whyoleg.cryptography.CryptographyAlgorithmId<Digest>,
	) {
		val r = ByteArrayReader(data)
		val scheme = r.readShort()
		val sigLen = r.readShort()
		val sig = r.readBytes(sigLen)
		val txHash = digest.doHash(hashAlg)
		val content = ByteArray(64) { 0x20 } + "TLS 1.3, server CertificateVerify".encodeToByteArray() + byteArrayOf(0) + txHash
		when (serverPubKey) {
			is CertPublicKey.Ec -> {
				// RFC 8446 §4.2.3: validate scheme matches key curve
				val (expectedCurveOid, d) =
					when (scheme) {
						0x0403 -> OID_SECP256R1 to dev.whyoleg.cryptography.algorithms.SHA256
						0x0503 -> OID_SECP384R1 to dev.whyoleg.cryptography.algorithms.SHA384
						0x0603 -> OID_SECP521R1 to dev.whyoleg.cryptography.algorithms.SHA512
						else -> throw TlsException("TLS 1.3: unsupported ECDSA CertificateVerify scheme: 0x${scheme.toString(16)}")
					}
				if (serverPubKey.curveOid != expectedCurveOid) {
					throw TlsException("TLS 1.3: certificate curve ${serverPubKey.curveOid} does not match scheme 0x${scheme.toString(16)}")
				}
				val curve =
					when (serverPubKey.curveOid) {
						OID_SECP256R1 -> EC.Curve.P256
						OID_SECP384R1 -> EC.Curve.P384
						OID_SECP521R1 -> EC.Curve.P521
						else -> throw TlsException("Unknown curve: ${serverPubKey.curveOid}")
					}
				val ecdsa = CryptographyProvider.Default.get(ECDSA)
				val pk = ecdsa.publicKeyDecoder(curve).decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, serverPubKey.point)
				if (!pk.signatureVerifier(d, ECDSA.SignatureFormat.DER).tryVerifySignatureBlocking(content, sig)) {
					throw TlsException("TLS 1.3: CertificateVerify ECDSA verification failed")
				}
			}
			is CertPublicKey.Rsa -> {
				// RFC 8446 §4.4.3: only RSA-PSS schemes permitted for TLS 1.3 CertificateVerify
				val d =
					when (scheme) {
						0x0804 -> dev.whyoleg.cryptography.algorithms.SHA256
						0x0805 -> dev.whyoleg.cryptography.algorithms.SHA384
						0x0806 -> dev.whyoleg.cryptography.algorithms.SHA512
						else -> throw TlsException("TLS 1.3: unsupported RSA-PSS CertificateVerify scheme: 0x${scheme.toString(16)}")
					}
				val rsa = CryptographyProvider.Default.get(RSA.PSS)
				val pk = rsa.publicKeyDecoder(d).decodeFromByteArrayBlocking(RSA.PublicKey.Format.DER, serverPubKey.spkiDer)
				if (!pk.signatureVerifier().tryVerifySignatureBlocking(content, sig)) {
					throw TlsException("TLS 1.3: CertificateVerify RSA-PSS verification failed")
				}
			}
		}
	}

	private fun verifyTls13ServerFinished(
		data: ByteArray,
		serverHsSecret: ByteArray,
		hashAlg: dev.whyoleg.cryptography.CryptographyAlgorithmId<Digest>,
		ks: Tls13KeySchedule,
	) {
		val txHash = digest.doHash(hashAlg)
		val expected = ks.finishedVerifyData(serverHsSecret, txHash)
		if (!data.contentEquals(expected)) throw TlsException("TLS 1.3: ServerFinished failed")
	}

	// ==================== TLS 1.2 ====================

	private suspend fun negotiateTls12(serverHello: TlsServerHello) {
		var serverPublicKey: CertPublicKey? = null
		var ecdhServerPoint: ByteArray? = null
		var ecdhCurve: CurveInfo? = null
		var certRequested = false

		loop@ while (true) {
			val msg = readHandshakeMessage()
			when (msg.type) {
				TlsHandshakeType.Certificate -> {
					val certs = parseCertificatesDer(msg.data)
					if (certs.isEmpty()) throw TlsException("No certificate")
					if (verifyCertificates) validateCertificateChain(certs, serverName)
					serverPublicKey = extractPublicKeyFromCertificate(certs.first())
				}
				TlsHandshakeType.ServerKeyExchange -> {
					val r = ByteArrayReader(msg.data)
					val curve = r.readCurveParams()
					val point = r.readECPoint(curve.fieldSize)
					val hs = r.readHashAndSign() ?: throw TlsException("Unknown hash and sign")
					val params = byteArrayOf(ServerKeyExchangeType.NamedCurve.code.toByte(), (curve.code.toInt() shr 8).toByte(), curve.code.toByte(), point.size.toByte()) + point
					val signed = clientRandom + serverHello.serverRandom + params
					val sigLen = r.readShort()
					val sig = r.readBytes(sigLen)
					verifyServerKeyExchangeSignature(serverPublicKey ?: throw TlsException("Server certificate required but not received"), signed, sig, hs)
					ecdhServerPoint = point
					ecdhCurve = curve
				}
				TlsHandshakeType.CertificateRequest -> {
					certRequested = true
				}
				TlsHandshakeType.ServerDone -> break@loop
				else -> throw TlsException("Unexpected: ${msg.type}")
			}
		}

		// RFC 5246 §7.4.6: respond to CertificateRequest with empty Certificate
		if (certRequested) {
			sendHandshakeRecord(TlsHandshakeType.Certificate) {
				writeByte(0) // cert_list_length high byte
				writeByte(0) // cert_list_length mid byte
				writeByte(0) // cert_list_length low byte
			}
		}

		val hmacDigest = digestAlgorithmForSuite(negotiatedSuite)
		val preSecret: ByteArray

		when (negotiatedSuite.exchangeType) {
			ExchangeType.ECDHE -> {
				val cc =
					when (ecdhCurve ?: throw TlsException("Missing EC curve parameters")) {
						CurveInfo.Secp256r1 -> EC.Curve.P256
						CurveInfo.Secp384r1 -> EC.Curve.P384
						CurveInfo.Secp521r1 -> EC.Curve.P521
					}
				val kp = ecdh.keyPairGenerator(cc).generateKeyBlocking()
				val cpub = kp.publicKey.encodeToByteArrayBlocking(EC.PublicKey.Format.RAW)
				val spub = ecdh.publicKeyDecoder(cc).decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, ecdhServerPoint ?: throw TlsException("Missing server EC public key"))
				preSecret = kp.privateKey.sharedSecretGenerator().generateSharedSecretToByteArrayBlocking(spub)
				sendHandshakeRecord(TlsHandshakeType.ClientKeyExchange) { writeECPoint(cpub) }
			}
			ExchangeType.RSA -> {
				preSecret = ByteArray(48)
				CryptographyRandom.nextBytes(preSecret)
				preSecret[0] = 3
				preSecret[1] = 3
				val rsaKey = serverPublicKey as CertPublicKey.Rsa
				val rsa = CryptographyProvider.Default.get(RSA.PKCS1)
				// Digest parameter is required by the API but unused for RSAES-PKCS1-v1_5 encryption
				val pk = rsa.publicKeyDecoder(SHA256).decodeFromByteArrayBlocking(RSA.PublicKey.Format.DER, rsaKey.spkiDer)
				val enc = pk.encryptor().encryptBlocking(preSecret)
				sendHandshakeRecord(TlsHandshakeType.ClientKeyExchange) {
					writeShort(enc.size.toShort())
					write(enc)
				}
			}
		}

		val masterSecret =
			if (useExtendedMasterSecret) {
				// RFC 7627: use session_hash (transcript through ClientKeyExchange) instead of random values
				val sessionHash = digest.doHash(hmacDigest)
				deriveExtendedMasterSecret(preSecret, sessionHash, hmacDigest)
			} else {
				deriveMasterSecret(preSecret, clientRandom, serverHello.serverRandom, hmacDigest)
			}
		preSecret.fill(0)
		val material =
			deriveKeyMaterial(
				masterSecret,
				serverHello.serverRandom + clientRandom,
				negotiatedSuite.keyStrengthBytes,
				negotiatedSuite.macStrengthBytes,
				negotiatedSuite.fixedIvLength,
				hmacDigest,
			)
		tls12Cipher = GcmTlsCipher(negotiatedSuite, KeyMaterial(material, negotiatedSuite.keyStrengthBytes, negotiatedSuite.macStrengthBytes, negotiatedSuite.fixedIvLength))

		// Send ChangeCipherSpec + Finished
		rawOutput.writeRecordBytes(TlsRecordType.ChangeCipherSpec, byteArrayOf(1))
		val checksum = digest.doHash(digestAlgorithmForSuite(negotiatedSuite))
		val fin = clientFinished(checksum, masterSecret, hmacDigest)
		sendEncryptedHandshakeRecord(TlsHandshakeType.Finished, fin)

		// Receive CCS + Finished
		val serverFinMsg = readEncryptedTls12HandshakeMessage(tls12Cipher)
		val expected = serverFinished(digest.doHash(hmacDigest), masterSecret, serverFinMsg.data.size, hmacDigest)
		if (!serverFinMsg.data.contentEquals(expected)) throw TlsException("ServerFinished failed")

		// Wipe key material
		masterSecret.fill(0)
		material.fill(0)
	}

	// --- Helpers ---

	private suspend fun sendHandshakeRecord(
		type: TlsHandshakeType,
		block: kotlinx.io.Sink.() -> Unit,
	) {
		val body = Buffer().apply(block).readByteArray()
		val record = buildHandshakeType(type, body.size) + body
		digest.update(record)
		rawOutput.writeRecordBytes(TlsRecordType.Handshake, record)
	}

	private suspend fun sendEncryptedHandshakeRecord(
		type: TlsHandshakeType,
		body: ByteArray,
	) {
		val record = buildHandshakeType(type, body.size) + body
		digest.update(record)
		val encrypted = tls12Cipher.encrypt(record, 0, record.size, TlsRecordType.Handshake)
		rawOutput.writeRecordBytes(TlsRecordType.Handshake, encrypted)
	}

	private suspend fun readEncryptedTls12HandshakeMessage(cipher: GcmTlsCipher): TlsHandshakeMessage {
		val encBuffer = HandshakeBuffer()
		while (true) {
			val msg = encBuffer.next()
			if (msg != null) return msg

			val record = rawInput.readTlsRecord()
			when (record.type) {
				TlsRecordType.ChangeCipherSpec -> continue
				TlsRecordType.Alert -> throw TlsException("Alert during handshake")
				TlsRecordType.Handshake -> {
					val pt = cipher.decrypt(record.data, record.offset, record.length, record.type)
					encBuffer.append(pt)
				}
				else -> throw TlsException("Unexpected: ${record.type}")
			}
		}
	}

	private fun verifyServerKeyExchangeSignature(
		serverPubKey: CertPublicKey,
		signedData: ByteArray,
		signature: ByteArray,
		hashAndSign: HashAndSignInfo,
	) {
		// RSA-PSS uses hash code 8 ("Intrinsic") — handle before digestAlgorithmForHash
		if (serverPubKey is CertPublicKey.Rsa && hashAndSign.hashCode == 8) {
			// RSA-PSS: actual digest is encoded in signCode (4=SHA256, 5=SHA384, 6=SHA512)
			val pssDigest =
				when (hashAndSign.signCode) {
					4 -> dev.whyoleg.cryptography.algorithms.SHA256
					5 -> dev.whyoleg.cryptography.algorithms.SHA384
					6 -> dev.whyoleg.cryptography.algorithms.SHA512
					else -> throw TlsException("Unsupported RSA-PSS scheme: 0x08${hashAndSign.signCode.toString(16).padStart(2, '0')}")
				}
			val pk =
				CryptographyProvider.Default
					.get(RSA.PSS)
					.publicKeyDecoder(pssDigest)
					.decodeFromByteArrayBlocking(RSA.PublicKey.Format.DER, serverPubKey.spkiDer)
			if (!pk.signatureVerifier().tryVerifySignatureBlocking(signedData, signature)) {
				throw TlsException("RSA-PSS sig failed")
			}
			return
		}

		val digestAlg = digestAlgorithmForHash(hashAndSign.hashCode)
		when (serverPubKey) {
			is CertPublicKey.Ec -> {
				val curve =
					when (serverPubKey.curveOid) {
						OID_SECP256R1 -> EC.Curve.P256
						OID_SECP384R1 -> EC.Curve.P384
						OID_SECP521R1 -> EC.Curve.P521
						else -> throw TlsException("Unknown curve")
					}
				val pk =
					CryptographyProvider.Default
						.get(ECDSA)
						.publicKeyDecoder(curve)
						.decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, serverPubKey.point)
				if (!pk.signatureVerifier(digestAlg, ECDSA.SignatureFormat.DER).tryVerifySignatureBlocking(signedData, signature)) {
					throw TlsException("ECDSA sig failed")
				}
			}
			is CertPublicKey.Rsa -> {
				val pk =
					CryptographyProvider.Default
						.get(RSA.PKCS1)
						.publicKeyDecoder(digestAlg)
						.decodeFromByteArrayBlocking(RSA.PublicKey.Format.DER, serverPubKey.spkiDer)
				if (!pk.signatureVerifier().tryVerifySignatureBlocking(signedData, signature)) {
					throw TlsException("RSA sig failed")
				}
			}
		}
	}

	companion object {
		// RFC 8446 §4.1.4: HelloRetryRequest is a ServerHello with this specific random value
		// (SHA-256 of "HelloRetryRequest")
		private val HRR_RANDOM =
			byteArrayOf(
				0xCF.toByte(),
				0x21.toByte(),
				0xAD.toByte(),
				0x74.toByte(),
				0xE5.toByte(),
				0x9A.toByte(),
				0x61.toByte(),
				0x11.toByte(),
				0xBE.toByte(),
				0x1D.toByte(),
				0x8C.toByte(),
				0x02.toByte(),
				0x1E.toByte(),
				0x65.toByte(),
				0xB8.toByte(),
				0x91.toByte(),
				0xC2.toByte(),
				0xA2.toByte(),
				0x11.toByte(),
				0x16.toByte(),
				0x7A.toByte(),
				0xBB.toByte(),
				0x8C.toByte(),
				0x5E.toByte(),
				0x07.toByte(),
				0x9E.toByte(),
				0x09.toByte(),
				0xE2.toByte(),
				0xC8.toByte(),
				0xA8.toByte(),
				0x33.toByte(),
				0x9C.toByte(),
			)

		// RFC 8446 §4.1.3 downgrade sentinels
		private val DOWNGRADE_TLS12 = byteArrayOf(0x44, 0x4F, 0x57, 0x4E, 0x47, 0x52, 0x44, 0x01) // "DOWNGRD" + 0x01
		private val DOWNGRADE_TLS11 = byteArrayOf(0x44, 0x4F, 0x57, 0x4E, 0x47, 0x52, 0x44, 0x00) // "DOWNGRD" + 0x00

		private fun checkDowngradeProtection(serverRandom: ByteArray) {
			if (serverRandom.size < 32) return
			val last8 = serverRandom.copyOfRange(24, 32)
			if (last8.contentEquals(DOWNGRADE_TLS12) || last8.contentEquals(DOWNGRADE_TLS11)) {
				throw TlsException("TLS downgrade attack detected: server random contains downgrade sentinel")
			}
		}

		private fun generateClientRandom(): ByteArray {
			val r = ByteArray(32)
			CryptographyRandom.nextBytes(r)
			return r
		}
	}
}

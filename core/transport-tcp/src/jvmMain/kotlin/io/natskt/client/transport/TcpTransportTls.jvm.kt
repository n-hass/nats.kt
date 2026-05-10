package io.natskt.client.transport

import io.ktor.network.sockets.connection
import io.ktor.network.tls.TlsException
import io.ktor.network.tls.addCertificateChain
import io.ktor.network.tls.tls
import io.natskt.client.TlsPrivateKeyAlgorithm
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal actual suspend fun performTlsUpgrade(transport: TcpTransport): Transport {
	val cfg = transport.tlsConfig
	val tlsSocket =
		try {
			transport.inner.tls(transport.context) {
				serverName = transport.serverName

				when {
					cfg.acceptAnyServerCertificate -> trustManager = AcceptAllTrustManager
					cfg.hasCustomTrust -> trustManager = trustManagerFromDer(cfg.caCertificatesDer)
				}

				if (cfg.hasClientCertificate) {
					val chain =
						CertificateFactory.getInstance("X.509").let { factory ->
							cfg.clientCertificateChainDer
								.map { factory.generateCertificate(ByteArrayInputStream(it)) as X509Certificate }
								.toTypedArray()
						}
					val key = parseClientPrivateKey(cfg.clientPrivateKeyDer!!, cfg.clientPrivateKeyAlgorithm)
					addCertificateChain(chain, key)
				}
			}
		} catch (cause: CancellationException) {
			throw cause
		} catch (cause: TlsException) {
			throw cause
		} catch (cause: Throwable) {
			// JSSE-thrown exceptions (e.g. sun.security.validator.ValidatorException, SSLException)
			// surface raw through Ktor's handshake. Wrap them so callers can match a single
			// cross-platform type.
			throw TlsException(cause.message ?: "TLS handshake failed", cause)
		}
	return TcpTransport(
		tlsSocket.connection(),
		transport.context,
		transport.selectorManager,
		transport.serverName,
		cfg,
	)
}

private fun trustManagerFromDer(caDer: List<ByteArray>): X509TrustManager {
	val factory = CertificateFactory.getInstance("X.509")
	val store =
		KeyStore.getInstance(KeyStore.getDefaultType()).apply {
			load(null, null)
			caDer.forEachIndexed { idx, der ->
				val cert = factory.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
				setCertificateEntry("natskt-ca-$idx", cert)
			}
		}
	val tmf =
		TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
			init(store)
		}
	return tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
		?: error("Could not find an X509TrustManager from supplied CA certificates")
}

private fun parseClientPrivateKey(
	der: ByteArray,
	algorithm: TlsPrivateKeyAlgorithm?,
): java.security.PrivateKey {
	val algName =
		when (algorithm) {
			TlsPrivateKeyAlgorithm.Rsa -> "RSA"
			TlsPrivateKeyAlgorithm.Ec -> "EC"
			null -> error("Client private key algorithm is required (use clientCertificate(...) which auto-detects)")
		}
	return KeyFactory.getInstance(algName).generatePrivate(PKCS8EncodedKeySpec(der))
}

private object AcceptAllTrustManager : X509TrustManager {
	override fun checkClientTrusted(
		chain: Array<X509Certificate>?,
		authType: String?,
	) {}

	override fun checkServerTrusted(
		chain: Array<X509Certificate>?,
		authType: String?,
	) {}

	override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

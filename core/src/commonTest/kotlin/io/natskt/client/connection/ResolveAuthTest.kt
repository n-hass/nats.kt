package io.natskt.client.connection

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EdDSA
import io.natskt.api.AuthPayload
import io.natskt.api.AuthProvider
import io.natskt.api.Credentials
import io.natskt.internal.ServerOperation
import io.natskt.nkeys.NKeySeed
import io.natskt.nkeys.NKeyType
import io.natskt.nkeys.NKeys
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class ResolveAuthTest {
	private fun defaultInfo(nonce: String? = "test_nonce_1234567890"): ServerOperation.InfoOp =
		ServerOperation.InfoOp(
			serverId = "test",
			serverName = "srv",
			version = "1",
			go = "go",
			host = "localhost",
			port = 4222,
			headers = true,
			maxPayload = 1024,
			proto = 1,
			clientId = null,
			authRequired = true,
			tlsRequired = null,
			tlsVerify = null,
			tlsAvailable = null,
			connectUrls = null,
			wsConnectUrls = null,
			ldm = null,
			gitCommit = null,
			jetstream = null,
			ip = null,
			clientIp = null,
			nonce = nonce,
			cluster = null,
			domain = null,
			xkey = null,
		)

	@Test
	fun `given null credentials uses url credentials`() =
		runTest {
			val eng = engine(url = "nats://user:pass@localhost:4222")
			val auth = eng.resolveAuth(defaultInfo(), credentials = null)
			assertEquals("user", auth.username)
			assertEquals("pass", auth.password)
			assertNull(auth.jwt)
			assertNull(auth.signature)
			assertNull(auth.nkey)
			assertNull(auth.authToken)
		}

	@Test
	fun `given password credentials uses provided user and password`() =
		runTest {
			val eng = engine()
			val creds = Credentials.Password("testuser", "testpass")
			val auth = eng.resolveAuth(defaultInfo(), creds)
			assertEquals("testuser", auth.username)
			assertEquals("testpass", auth.password)
			assertNull(auth.jwt)
			assertNull(auth.signature)
			assertNull(auth.nkey)
		}

	@Test
	fun `given blank password credentials falls back to url credentials`() =
		runTest {
			val eng = engine(url = "nats://user:pass@localhost:4222")
			val creds = Credentials.Password("", "")
			val auth = eng.resolveAuth(defaultInfo(), creds)
			assertEquals("user", auth.username)
			assertEquals("pass", auth.password)
		}

	@Test
	fun `given jwt credentials builds nkey auth with jwt and signature`() =
		runTest {
			val eng = engine()
			val seedBytes = ByteArray(32) { (it * 7).toByte() }
			val nkeySeed = NKeySeed.encodeSeed(NKeyType.User, seedBytes)
			val expectedNkey = NKeySeed.parse(nkeySeed).getPublicKey()
			val jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9"

			val creds = Credentials.Jwt(jwt, nkeySeed)
			val auth = eng.resolveAuth(defaultInfo(), creds)

			assertEquals(jwt, auth.jwt)
			assertNull(auth.username)
			assertNull(auth.password)
			// signature and nkey should be populated from nkey seed
			assertEquals(expectedNkey, auth.nkey)
			assertNotNull(auth.signature)
		}

	@Test
	fun `given nkey credentials builds nkey auth with signature`() =
		runTest {
			val eng = engine()
			val seedBytes = ByteArray(32) { (it * 7).toByte() }
			val nkeySeed = NKeySeed.encodeSeed(NKeyType.User, seedBytes)
			val expectedNkey = NKeySeed.parse(nkeySeed).getPublicKey()

			val creds = Credentials.Nkey(nkeySeed)
			val auth = eng.resolveAuth(defaultInfo(), creds)

			assertNull(auth.jwt)
			assertNull(auth.username)
			assertNull(auth.password)
			assertEquals(expectedNkey, auth.nkey)
			assertNotNull(auth.signature)
		}

	@Test
	fun `given file credentials parses and builds nkey auth`() =
		runTest {
			val eng = engine()
			val seedBytes = ByteArray(32) { (it * 7).toByte() }
			val seedString = NKeySeed.encodeSeed(NKeyType.User, seedBytes)
			val expectedNkey = NKeySeed.parse(seedString).getPublicKey()
			val jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9"
			val credsText =
				buildString {
					appendLine("# Sample creds generated for tests")
					appendLine("-----BEGIN NATS USER JWT-----")
					appendLine(jwt)
					appendLine("------END NATS USER JWT------")
					appendLine()
					appendLine("-----BEGIN USER NKEY SEED-----")
					appendLine(seedString)
					appendLine("------END USER NKEY SEED------")
				}

			val creds = Credentials.File(credsText)
			val auth = eng.resolveAuth(defaultInfo(), creds)

			assertEquals(jwt, auth.jwt)
			assertEquals(expectedNkey, auth.nkey)
			assertNotNull(auth.signature)
		}

	@Test
	fun `given custom credentials with password and nkey uses both`() =
		runTest {
			val eng = engine()
			val seedBytes = ByteArray(32) { (it * 7).toByte() }
			val nkeySeed = NKeySeed.encodeSeed(NKeyType.User, seedBytes)
			val nkey = NKeySeed.parse(nkeySeed)

			val creds =
				Credentials.Custom(
					provider =
						AuthProvider { info ->
							AuthPayload(
								username = "customuser",
								password = "custompass",
								nkey = nkey.getPublicKey(),
								signature = signNonce(nkeySeed, info),
							)
						},
				)
			val defaultInfo = defaultInfo()
			val auth = eng.resolveAuth(defaultInfo, creds)

			assertEquals("customuser", auth.username)
			assertEquals("custompass", auth.password)
			assertNull(auth.jwt)
			assertEquals(nkey.getPublicKey(), auth.nkey)
			val key =
				CryptographyProvider.Default
					.get(EdDSA)
					.privateKeyDecoder(EdDSA.Curve.Ed25519)
					.decodeFromByteArray(EdDSA.PrivateKey.Format.RAW, seedBytes)
			assertTrue {
				key
					.getPublicKey()
					.signatureVerifier()
					.tryVerifySignature(defaultInfo.nonce!!.encodeToByteArray(), Base64.decode(auth.signature!!))
			}
		}

	@Test
	fun `given custom credentials returns specified auth payload`() =
		runTest {
			val eng = engine()
			val jwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.jwt"

			val nkeySeedBytes = ByteArray(32) { (it * 13).toByte() }
			val standaloneNkeySeed = NKeySeed.encodeSeed(NKeyType.User, nkeySeedBytes)

			val creds =
				Credentials.Custom(
					provider =
						AuthProvider { info ->
							AuthPayload(
								jwt = jwtToken,
								nkey = "my custom key",
								signature =	null,
							)
						},
				)
			val auth = eng.resolveAuth(defaultInfo(), creds)

			assertEquals(jwtToken, auth.jwt)
			assertEquals(
				"my custom key",
				auth.nkey,
			)
			assertNull(auth.signature)
		}

	@Test
	fun `given custom credentials with all auth types returns complete auth payload`() =
		runTest {
			val eng = engine()
			val jwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.jwt"

			val nkeySeedBytes = ByteArray(32) { (it * 13).toByte() }
			val standaloneNkeySeed = NKeySeed.encodeSeed(NKeyType.User, nkeySeedBytes)

			val creds =
				Credentials.Custom(
					provider =
						AuthProvider { info ->
							AuthPayload(
								username = "customuser",
								password = "custompass",
								jwt = jwtToken,
								nkey = NKeys.parseSeed(standaloneNkeySeed).getPublicKey(),
								signature = signNonce(standaloneNkeySeed, info),
							)
						},
				)
			val auth = eng.resolveAuth(defaultInfo(), creds)

			// Should use password for user/pass
			assertEquals("customuser", auth.username)
			assertEquals("custompass", auth.password)
			assertEquals(jwtToken, auth.jwt)
			assertEquals(
				NKeys.parseSeed(standaloneNkeySeed).getPublicKey(),
				auth.nkey,
			)
			assertNotNull(auth.signature)
		}
}

package io.natskt.client.connection

import io.github.andreypfau.curve25519.ed25519.Ed25519
import io.natskt.api.AuthPayload
import io.natskt.api.Credentials
import io.natskt.internal.ServerOperation
import io.natskt.nkeys.NKeySeed
import io.natskt.nkeys.NKeyType
import io.natskt.nkeys.NKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
	fun `given null credentials uses url credentials`() {
		val eng = engine(url = "nats://user:pass@localhost:4222")
		val auth = eng.resolveAuth(defaultInfo(), credentials = null)
		assertEquals("user", auth.user)
		assertEquals("pass", auth.pass)
		assertNull(auth.jwt)
		assertNull(auth.signature)
		assertNull(auth.nkeyPublic)
		assertNull(auth.authToken)
	}

	@Test
	fun `given password credentials uses provided user and password`() {
		val eng = engine()
		val creds = Credentials.Password("testuser", "testpass")
		val auth = eng.resolveAuth(defaultInfo(), creds)
		assertEquals("testuser", auth.user)
		assertEquals("testpass", auth.pass)
		assertNull(auth.jwt)
		assertNull(auth.signature)
		assertNull(auth.nkeyPublic)
	}

	@Test
	fun `given blank password credentials falls back to url credentials`() {
		val eng = engine(url = "nats://user:pass@localhost:4222")
		val creds = Credentials.Password("", "")
		val auth = eng.resolveAuth(defaultInfo(), creds)
		assertEquals("user", auth.user)
		assertEquals("pass", auth.pass)
	}

	@Test
	fun `given jwt credentials builds nkey auth with jwt and signature`() {
		val eng = engine()
		val seedBytes = ByteArray(32) { (it * 7).toByte() }
		val privateKey =
			Ed25519.keyFromSeed(seedBytes)
		val nkeySeed = NKeySeed.encodeSeed(NKeyType.User, privateKey)
		val jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9"

		val creds = Credentials.Jwt(jwt, nkeySeed)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		assertEquals(jwt, auth.jwt)
		assertNull(auth.user)
		assertNull(auth.pass)
		// signature and nkey should be populated from nkey seed
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
			auth.nkeyPublic,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given nkey credentials builds nkey auth with signature`() {
		val eng = engine()
		val seedBytes = ByteArray(32) { (it * 7).toByte() }
		val privateKey =
			Ed25519.keyFromSeed(seedBytes)
		val nkeySeed = NKeySeed.encodeSeed(NKeyType.User, privateKey)

		val creds = Credentials.Nkey(nkeySeed)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		assertNull(auth.jwt)
		assertNull(auth.user)
		assertNull(auth.pass)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
			auth.nkeyPublic,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given file credentials parses and builds nkey auth`() {
		val eng = engine()
		val seedBytes = ByteArray(32) { (it * 7).toByte() }
		val privateKey =
			Ed25519.keyFromSeed(seedBytes)
		val seedString = NKeySeed.encodeSeed(NKeyType.User, privateKey)
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
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
			auth.nkeyPublic,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given custom credentials with password and nkey uses both`() {
		val eng = engine()
		val seedBytes = ByteArray(32) { (it * 7).toByte() }
		val privateKey = Ed25519.keyFromSeed(seedBytes)
		val nkeySeed = NKeySeed.encodeSeed(NKeyType.User, privateKey)
		val nkey = NKeySeed.parse(nkeySeed)

		val creds =
			Credentials.Custom(
				provider =
					Credentials.AuthProvider { info ->
						AuthPayload(
							user = "customuser",
							pass = "custompass",
							nkeyPublic = NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
							signature = signNonce(nkeySeed, info),
						)
					},
			)
		val defaultInfo = defaultInfo()
		val auth = eng.resolveAuth(defaultInfo, creds)

		assertEquals("customuser", auth.user)
		assertEquals("custompass", auth.pass)
		assertNull(auth.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
			auth.nkeyPublic,
		)
		assertEquals(nkey.signToBase64(defaultInfo.nonce!!.encodeToByteArray()), auth.signature)
	}

	@Test
	fun `given custom credentials returns specified auth payload`() {
		val eng = engine()
		val jwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.jwt"

		val nkeySeedBytes = ByteArray(32) { (it * 13).toByte() }
		val nkeyPrivateKey = Ed25519.keyFromSeed(nkeySeedBytes)
		val standaloneNkeySeed = NKeySeed.encodeSeed(NKeyType.User, nkeyPrivateKey)

		val creds =
			Credentials.Custom(
				provider =
					Credentials.AuthProvider { info ->
						AuthPayload(
							jwt = jwtToken,
							nkeyPublic = "my custom key",
							signature =	null,
						)
					},
			)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		assertEquals(jwtToken, auth.jwt)
		assertEquals(
			"my custom key",
			auth.nkeyPublic,
		)
		assertNull(auth.signature)
	}

	@Test
	fun `given custom credentials with all auth types returns complete auth payload`() {
		val eng = engine()
		val jwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.jwt"

		val nkeySeedBytes = ByteArray(32) { (it * 13).toByte() }
		val nkeyPrivateKey = Ed25519.keyFromSeed(nkeySeedBytes)
		val standaloneNkeySeed = NKeySeed.encodeSeed(NKeyType.User, nkeyPrivateKey)

		val creds =
			Credentials.Custom(
				provider =
					Credentials.AuthProvider { info ->
						AuthPayload(
							user = "customuser",
							pass = "custompass",
							jwt = jwtToken,
							nkeyPublic = NKeys.parseSeed(standaloneNkeySeed).publicKey,
							signature = signNonce(standaloneNkeySeed, info),
						)
					},
			)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		// Should use password for user/pass
		assertEquals("customuser", auth.user)
		assertEquals("custompass", auth.pass)
		assertEquals(jwtToken, auth.jwt)
		assertEquals(
			NKeys.parseSeed(standaloneNkeySeed).publicKey,
			auth.nkeyPublic,
		)
		assertNotNull(auth.signature)
	}
}

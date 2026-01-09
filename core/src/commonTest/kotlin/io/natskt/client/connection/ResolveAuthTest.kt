package io.natskt.client.connection

import io.github.andreypfau.curve25519.ed25519.Ed25519
import io.natskt.api.Credentials
import io.natskt.internal.ServerOperation
import io.natskt.nkeys.NKeySeed
import io.natskt.nkeys.NKeyType
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
		assertNull(auth.nkey)
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
		assertNull(auth.nkey)
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
			auth.nkey,
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
			auth.nkey,
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
			auth.nkey,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given custom credentials with password and jwt merges both auth types`() {
		val eng = engine()
		val seedBytes = ByteArray(32) { (it * 7).toByte() }
		val privateKey =
			Ed25519.keyFromSeed(seedBytes)
		val nkeySeed = NKeySeed.encodeSeed(NKeyType.User, privateKey)
		val jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9"

		val password = Credentials.Password("customuser", "custompass")
		val jwtCred = Credentials.Jwt(jwt, nkeySeed)
		val creds = Credentials.Custom(jwt = jwtCred, password = password)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		// Should have password credentials
		assertEquals("customuser", auth.user)
		assertEquals("custompass", auth.pass)
		// Should have JWT and nkey from JWT credential
		assertEquals(jwt, auth.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
			auth.nkey,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given custom credentials with password and file merges both auth types`() {
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

		val password = Credentials.Password("customuser", "custompass")
		val fileCred = Credentials.File(credsText)
		val creds = Credentials.Custom(file = fileCred, password = password)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		// Should have password credentials
		assertEquals("customuser", auth.user)
		assertEquals("custompass", auth.pass)
		// Should have JWT and nkey from File credential
		assertEquals(jwt, auth.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
			auth.nkey,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given custom credentials with only jwt uses jwt auth fields`() {
		val eng = engine()
		val seedBytes = ByteArray(32) { (it * 7).toByte() }
		val privateKey = Ed25519.keyFromSeed(seedBytes)
		val nkeySeed = NKeySeed.encodeSeed(NKeyType.User, privateKey)
		val jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9"

		val jwtCred = Credentials.Jwt(jwt, nkeySeed)
		val creds = Credentials.Custom(jwt = jwtCred)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		assertNull(auth.user)
		assertNull(auth.pass)
		assertEquals(jwt, auth.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
			auth.nkey,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given custom credentials with only file uses file auth fields`() {
		val eng = engine()
		val seedBytes = ByteArray(32) { (it * 7).toByte() }
		val privateKey = Ed25519.keyFromSeed(seedBytes)
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

		val fileCred = Credentials.File(credsText)
		val creds = Credentials.Custom(file = fileCred)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		assertNull(auth.user)
		assertNull(auth.pass)
		assertEquals(jwt, auth.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
			auth.nkey,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given custom credentials with only nkey uses nkey auth fields`() {
		val eng = engine()
		val seedBytes = ByteArray(32) { (it * 7).toByte() }
		val privateKey = Ed25519.keyFromSeed(seedBytes)
		val nkeySeed = NKeySeed.encodeSeed(NKeyType.User, privateKey)

		val nkeyCred = Credentials.Nkey(nkeySeed)
		val creds = Credentials.Custom(nkey = nkeyCred)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		assertNull(auth.user)
		assertNull(auth.pass)
		assertNull(auth.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
			auth.nkey,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given custom credentials with password and nkey uses both`() {
		val eng = engine()
		val seedBytes = ByteArray(32) { (it * 7).toByte() }
		val privateKey = Ed25519.keyFromSeed(seedBytes)
		val nkeySeed = NKeySeed.encodeSeed(NKeyType.User, privateKey)

		val password = Credentials.Password("customuser", "custompass")
		val nkeyCred = Credentials.Nkey(nkeySeed)
		val creds = Credentials.Custom(password = password, nkey = nkeyCred)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		assertEquals("customuser", auth.user)
		assertEquals("custompass", auth.pass)
		assertNull(auth.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
			auth.nkey,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given custom credentials with jwt and nkey prefers jwt over nkey`() {
		val eng = engine()
		val jwtSeedBytes = ByteArray(32) { (it * 7).toByte() }
		val jwtPrivateKey = Ed25519.keyFromSeed(jwtSeedBytes)
		val jwtNkeySeed = NKeySeed.encodeSeed(NKeyType.User, jwtPrivateKey)
		val jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9"

		val nkeySeedBytes = ByteArray(32) { (it * 11).toByte() }
		val nkeyPrivateKey = Ed25519.keyFromSeed(nkeySeedBytes)
		val standaloneNkeySeed = NKeySeed.encodeSeed(NKeyType.User, nkeyPrivateKey)

		val jwtCred = Credentials.Jwt(jwt, jwtNkeySeed)
		val nkeyCred = Credentials.Nkey(standaloneNkeySeed)
		val creds = Credentials.Custom(jwt = jwtCred, nkey = nkeyCred)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		// Should use JWT credentials, not standalone nkey
		assertEquals(jwt, auth.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, jwtPrivateKey.publicKey().toByteArray()),
			auth.nkey,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given custom credentials with file and nkey prefers file over nkey`() {
		val eng = engine()
		val fileSeedBytes = ByteArray(32) { (it * 7).toByte() }
		val filePrivateKey = Ed25519.keyFromSeed(fileSeedBytes)
		val fileSeedString = NKeySeed.encodeSeed(NKeyType.User, filePrivateKey)
		val jwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9"
		val credsText =
			buildString {
				appendLine("# Sample creds generated for tests")
				appendLine("-----BEGIN NATS USER JWT-----")
				appendLine(jwt)
				appendLine("------END NATS USER JWT------")
				appendLine()
				appendLine("-----BEGIN USER NKEY SEED-----")
				appendLine(fileSeedString)
				appendLine("------END USER NKEY SEED------")
			}

		val nkeySeedBytes = ByteArray(32) { (it * 11).toByte() }
		val nkeyPrivateKey = Ed25519.keyFromSeed(nkeySeedBytes)
		val standaloneNkeySeed = NKeySeed.encodeSeed(NKeyType.User, nkeyPrivateKey)

		val fileCred = Credentials.File(credsText)
		val nkeyCred = Credentials.Nkey(standaloneNkeySeed)
		val creds = Credentials.Custom(file = fileCred, nkey = nkeyCred)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		// Should use File credentials, not standalone nkey
		assertEquals(jwt, auth.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, filePrivateKey.publicKey().toByteArray()),
			auth.nkey,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given custom credentials with jwt and file prefers jwt over file`() {
		val eng = engine()
		val jwtSeedBytes = ByteArray(32) { (it * 7).toByte() }
		val jwtPrivateKey = Ed25519.keyFromSeed(jwtSeedBytes)
		val jwtNkeySeed = NKeySeed.encodeSeed(NKeyType.User, jwtPrivateKey)
		val jwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.jwt"

		val fileSeedBytes = ByteArray(32) { (it * 11).toByte() }
		val filePrivateKey = Ed25519.keyFromSeed(fileSeedBytes)
		val fileSeedString = NKeySeed.encodeSeed(NKeyType.User, filePrivateKey)
		val fileJwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.file"
		val credsText =
			buildString {
				appendLine("# Sample creds generated for tests")
				appendLine("-----BEGIN NATS USER JWT-----")
				appendLine(fileJwt)
				appendLine("------END NATS USER JWT------")
				appendLine()
				appendLine("-----BEGIN USER NKEY SEED-----")
				appendLine(fileSeedString)
				appendLine("------END USER NKEY SEED------")
			}

		val jwtCred = Credentials.Jwt(jwtToken, jwtNkeySeed)
		val fileCred = Credentials.File(credsText)
		val creds = Credentials.Custom(jwt = jwtCred, file = fileCred)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		// Should use JWT credentials, not File credentials
		assertEquals(jwtToken, auth.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, jwtPrivateKey.publicKey().toByteArray()),
			auth.nkey,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given custom credentials with jwt file and nkey prefers jwt`() {
		val eng = engine()
		val jwtSeedBytes = ByteArray(32) { (it * 7).toByte() }
		val jwtPrivateKey = Ed25519.keyFromSeed(jwtSeedBytes)
		val jwtNkeySeed = NKeySeed.encodeSeed(NKeyType.User, jwtPrivateKey)
		val jwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.jwt"

		val fileSeedBytes = ByteArray(32) { (it * 11).toByte() }
		val filePrivateKey = Ed25519.keyFromSeed(fileSeedBytes)
		val fileSeedString = NKeySeed.encodeSeed(NKeyType.User, filePrivateKey)
		val fileJwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.file"
		val credsText =
			buildString {
				appendLine("# Sample creds generated for tests")
				appendLine("-----BEGIN NATS USER JWT-----")
				appendLine(fileJwt)
				appendLine("------END NATS USER JWT------")
				appendLine()
				appendLine("-----BEGIN USER NKEY SEED-----")
				appendLine(fileSeedString)
				appendLine("------END USER NKEY SEED------")
			}

		val nkeySeedBytes = ByteArray(32) { (it * 13).toByte() }
		val nkeyPrivateKey = Ed25519.keyFromSeed(nkeySeedBytes)
		val standaloneNkeySeed = NKeySeed.encodeSeed(NKeyType.User, nkeyPrivateKey)

		val jwtCred = Credentials.Jwt(jwtToken, jwtNkeySeed)
		val fileCred = Credentials.File(credsText)
		val nkeyCred = Credentials.Nkey(standaloneNkeySeed)
		val creds = Credentials.Custom(jwt = jwtCred, file = fileCred, nkey = nkeyCred)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		// Should use JWT credentials (highest precedence)
		assertEquals(jwtToken, auth.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, jwtPrivateKey.publicKey().toByteArray()),
			auth.nkey,
		)
		assertNotNull(auth.signature)
	}

	@Test
	fun `given custom credentials with all four types uses password for user pass and jwt for nkey auth`() {
		val eng = engine()
		val password = Credentials.Password("customuser", "custompass")

		val jwtSeedBytes = ByteArray(32) { (it * 7).toByte() }
		val jwtPrivateKey = Ed25519.keyFromSeed(jwtSeedBytes)
		val jwtNkeySeed = NKeySeed.encodeSeed(NKeyType.User, jwtPrivateKey)
		val jwtToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.jwt"

		val fileSeedBytes = ByteArray(32) { (it * 11).toByte() }
		val filePrivateKey = Ed25519.keyFromSeed(fileSeedBytes)
		val fileSeedString = NKeySeed.encodeSeed(NKeyType.User, filePrivateKey)
		val fileJwt = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFZERTQSJ9.file"
		val credsText =
			buildString {
				appendLine("# Sample creds generated for tests")
				appendLine("-----BEGIN NATS USER JWT-----")
				appendLine(fileJwt)
				appendLine("------END NATS USER JWT------")
				appendLine()
				appendLine("-----BEGIN USER NKEY SEED-----")
				appendLine(fileSeedString)
				appendLine("------END USER NKEY SEED------")
			}

		val nkeySeedBytes = ByteArray(32) { (it * 13).toByte() }
		val nkeyPrivateKey = Ed25519.keyFromSeed(nkeySeedBytes)
		val standaloneNkeySeed = NKeySeed.encodeSeed(NKeyType.User, nkeyPrivateKey)

		val jwtCred = Credentials.Jwt(jwtToken, jwtNkeySeed)
		val fileCred = Credentials.File(credsText)
		val nkeyCred = Credentials.Nkey(standaloneNkeySeed)
		val creds = Credentials.Custom(password = password, jwt = jwtCred, file = fileCred, nkey = nkeyCred)
		val auth = eng.resolveAuth(defaultInfo(), creds)

		// Should use password for user/pass
		assertEquals("customuser", auth.user)
		assertEquals("custompass", auth.pass)
		// Should use JWT for nkey auth (highest precedence)
		assertEquals(jwtToken, auth.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, jwtPrivateKey.publicKey().toByteArray()),
			auth.nkey,
		)
		assertNotNull(auth.signature)
	}
}

package io.natskt.nkeys

import io.github.andreypfau.curve25519.ed25519.Ed25519
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class NKeysTest {
	@Test
	fun parseCredentialsExtractsJwtAndSeed() {
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
				appendLine("************************* IMPORTANT *************************")
				appendLine("NKEY Seed only compatible with synadia-managed operator")
				appendLine("-----BEGIN USER NKEY SEED-----")
				appendLine(seedString)
				appendLine("------END USER NKEY SEED------")
				appendLine("*************************************************************")
			}

		val creds = NKeys.parseCreds(credsText)
		assertEquals(jwt, creds.jwt)
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
			creds.seed.publicKey,
		)
	}

	@Test
	fun signNonceMatchesEd25519Signature() {
		val seedBytes = ByteArray(32) { it.toByte() }
		val privateKey = Ed25519.keyFromSeed(seedBytes)
		val seedString = NKeySeed.encodeSeed(NKeyType.User, privateKey)
		val parsed = NKeys.parseSeed(seedString)
		val nonce = "deadbeef"
		val expectedSignature = Base64.Default.encode(privateKey.sign(nonce.encodeToByteArray()))
		assertEquals(expectedSignature, parsed.signNonce(nonce))
		assertEquals(
			NKeySeed.encodePublicKey(NKeyType.User, privateKey.publicKey().toByteArray()),
			parsed.publicKey,
		)
	}
}

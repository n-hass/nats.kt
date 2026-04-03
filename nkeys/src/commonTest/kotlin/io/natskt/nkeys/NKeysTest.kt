package io.natskt.nkeys

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EdDSA
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class NKeysTest {
	@Test
	fun `parse credentials extracts JWT and seed`() =
		runTest {
			val seedBytes = ByteArray(32) { (it * 7).toByte() }
			val seedString = NKeySeed.encodeSeed(NKeyType.User, seedBytes)
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
				NKeys.parseSeed(seedString).getPublicKey(),
				creds.seed.getPublicKey(),
			)
		}

	@Test
	fun `sign nonce matches manual signature`() =
		runTest {
			val seedBytes = ByteArray(32) { it.toByte() }
			val eddsa = CryptographyProvider.Default.get(EdDSA)
			val key =
				eddsa
					.privateKeyDecoder(EdDSA.Curve.Ed25519)
					.decodeFromByteArray(EdDSA.PrivateKey.Format.RAW, seedBytes)
			val seedString = NKeySeed.encodeSeed(NKeyType.User, seedBytes)
			val parsed = NKeys.parseSeed(seedString)
			val nonce = "deadbeef"
			val payload = nonce.encodeToByteArray()
			val expectedSignature = key.signatureGenerator().generateSignature(payload)
			val parsedSignature = Base64.decode(parsed.signNonce(nonce))
			val verifier = key.getPublicKey().signatureVerifier()
			assertTrue(verifier.tryVerifySignature(payload, expectedSignature))
			assertTrue(verifier.tryVerifySignature(payload, parsedSignature))
			assertEquals(NKeys.parseSeed(seedString).getPublicKey(), parsed.getPublicKey())
		}

	@Test
	fun `decodes public key correctly`() =
		runTest {
			val seedBytes = ByteArray(32) { it.toByte() }
			val eddsa = CryptographyProvider.Default.get(EdDSA)
			val seedString = NKeySeed.encodeSeed(NKeyType.User, seedBytes)
			val parsed = NKeys.parseSeed(seedString)
			val key =
				eddsa
					.privateKeyDecoder(EdDSA.Curve.Ed25519)
					.decodeFromByteArray(EdDSA.PrivateKey.Format.RAW, seedBytes)
			val (type, publicKeyBytes) = NKeySeed.decodePublicKey(parsed.getPublicKey())
			assertEquals(NKeyType.User, type)
			assertEquals(
				key.getPublicKey().encodeToByteArray(EdDSA.PublicKey.Format.RAW).size,
				publicKeyBytes.size,
			)
			assertContentEquals(key.getPublicKey().encodeToByteArray(EdDSA.PublicKey.Format.RAW), publicKeyBytes)
		}

	@Test
	fun `decodes private key correctly`() =
		runTest {
			val seedBytes = ByteArray(32) { it.toByte() }
			val eddsa = CryptographyProvider.Default.get(EdDSA)
			val seedString = NKeySeed.encodeSeed(NKeyType.User, seedBytes)
			val parsed = NKeys.parseSeed(seedString)
			val key =
				eddsa
					.privateKeyDecoder(EdDSA.Curve.Ed25519)
					.decodeFromByteArray(EdDSA.PrivateKey.Format.RAW, seedBytes)
			assertEquals(
				key.encodeToByteArray(EdDSA.PrivateKey.Format.RAW).decodeToString(),
				parsed.privateKey.encodeToByteArray(EdDSA.PrivateKey.Format.RAW).decodeToString(),
			)
		}
}

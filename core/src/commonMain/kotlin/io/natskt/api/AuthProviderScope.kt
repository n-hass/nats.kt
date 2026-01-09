package io.natskt.api

import io.natskt.internal.ServerOperation
import io.natskt.nkeys.NKeySeed

public object AuthProviderScope {
	/**
	 * Sign the nonce from  a [ServerOperation.InfoOp].
	 *
	 * [nkeySeed] must be in format like: `SUAAABYOCUOCGKRRHA7UMTKULNRGS4DXP2CYZE42UGUK7NV5YTF5FWIXJQ`
	 *
	 * @return the Base-64 representation of the signed nonce
	 */
	public fun signNonce(
		nkeySeed: String,
		infoOp: ServerOperation.InfoOp,
	): String? = infoOp.nonce?.let { signWithNkey(nkeySeed, it) }

	/**
	 * [nkeySeed] must be in format like: `SUAAABYOCUOCGKRRHA7UMTKULNRGS4DXP2CYZE42UGUK7NV5YTF5FWIXJQ`
	 *
	 * @param nkeySeed a valid nkey seed value
	 * @param payload a string of bytes to sign
	 * @return the Base-64 representation of the signed bytes
	 */
	public fun signWithNkey(
		nkeySeed: String,
		payload: String,
	): String = signWithNkey(NKeySeed.parse(nkeySeed), payload.encodeToByteArray())

	/**
	 * @return the Base-64 representation of the signed bytes
	 */
	public fun signWithNkey(
		nkeySeed: NKeySeed,
		payload: ByteArray,
	): String = nkeySeed.signToBase64(payload)
}

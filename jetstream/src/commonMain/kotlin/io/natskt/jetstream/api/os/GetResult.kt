package io.natskt.jetstream.api.os

/**
 * The result of an eager [ObjectStoreBucket.get] call: the resolved
 * [ObjectInfo] and the full byte content held in memory.
 *
 * For larger objects, prefer [ObjectStoreBucket.getStream] which returns the
 * info up front and a [kotlinx.coroutines.flow.Flow] of chunks.
 */
public class GetResult(
	public val info: ObjectInfo,
	public val data: ByteArray,
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other == null || this::class != other::class) return false

		other as GetResult

		if (info != other.info) return false
		if (!data.contentEquals(other.data)) return false

		return true
	}

	override fun hashCode(): Int {
		var result = info.hashCode()
		result = 31 * result + data.contentHashCode()
		return result
	}

	override fun toString(): String = "GetResult(info=$info, data=ByteArray(size=${data.size}))"
}

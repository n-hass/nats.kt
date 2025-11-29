package io.natskt.jetstream.api.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
public object Base64Deserializer : KSerializer<ByteArray?> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Base64ByteArray", PrimitiveKind.STRING)

	override fun serialize(
		encoder: Encoder,
		value: ByteArray?,
	) {
		if (value == null) {
			encoder.encodeNull()
		} else {
			encoder.encodeString(Base64.encode(value))
		}
	}

	override fun deserialize(decoder: Decoder): ByteArray? {
		val string = decoder.decodeString()
		return if (string.isEmpty()) null else Base64.decode(string)
	}
}

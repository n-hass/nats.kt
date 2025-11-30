package io.natskt.jetstream.api.internal

import io.natskt.api.MessageHeaders
import io.natskt.internal.parseHeaders
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
public object HeadersBase64Serializer : KSerializer<MessageHeaders?> {
	override val descriptor: SerialDescriptor = SerialDescriptor("HeadersBase64Serializer", serialDescriptor<MessageHeaders>())

	@Deprecated("this should not be used")
	override fun serialize(
		encoder: Encoder,
		value: MessageHeaders?,
	) {
		encoder.encodeNull() // should never be used
	}

	override fun deserialize(decoder: Decoder): MessageHeaders? {
		val string = decoder.decodeString()
		if (string.isEmpty()) return null
		val decoded = Base64.decode(string).decodeToString()
		return parseHeaders(decoded)
	}
}

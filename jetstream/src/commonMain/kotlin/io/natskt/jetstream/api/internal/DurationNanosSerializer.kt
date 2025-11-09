package io.natskt.jetstream.api.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

internal object DurationNanosSerializer : KSerializer<Duration> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("kotlin.time.DurationAsNanoseconds", PrimitiveKind.LONG)

	override fun serialize(
		encoder: Encoder,
		value: Duration,
	) {
		encoder.encodeLong(value.inWholeNanoseconds)
	}

	override fun deserialize(decoder: Decoder): Duration {
		val nanos = decoder.decodeLong()
		return nanos.nanoseconds
	}
}

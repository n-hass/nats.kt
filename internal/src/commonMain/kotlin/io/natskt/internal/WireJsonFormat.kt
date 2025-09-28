package io.natskt.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val wireJsonFormat =
	Json {
		classDiscriminatorMode = ClassDiscriminatorMode.NONE
		explicitNulls = false
		ignoreUnknownKeys = true
	}

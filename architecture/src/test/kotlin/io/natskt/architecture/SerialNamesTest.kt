package io.natskt.architecture

import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import io.kotest.core.spec.style.FreeSpec

@Suppress("unused")
class SerialNamesTest :
	FreeSpec({
	})

fun KoPropertyDeclaration.hasSerialNameAnnotation(): Boolean = hasAnnotation { it.fullyQualifiedName == "kotlinx.serialization.SerialName" }

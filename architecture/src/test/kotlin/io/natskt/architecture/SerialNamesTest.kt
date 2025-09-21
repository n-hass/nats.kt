package io.natskt.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import io.kotest.core.spec.style.FreeSpec

@Suppress("unused")
class SerialNamesTest :
	FreeSpec({
		"serial names match property name" {
			Konsist
				.scopeFromProject()
				.classes()
				.withoutWhitelistedClasses()
				.filter { klass ->
					klass.hasAnnotation { it.fullyQualifiedName == "kotlinx.serialization.SerialName" }
				}.flatMap { klass ->
					klass.properties()
				}.forEach { property ->
					if (property.hasSerialNameAnnotation() && !property.text.contains("field")) {
						assert(
							property.name == property.name.toSnakeCase(),
						) {
							"${property.fullyQualifiedName} SerialName should match property name"
						}
					}
				}
		}
	})

fun KoPropertyDeclaration.hasSerialNameAnnotation(): Boolean = hasAnnotation { it.fullyQualifiedName == "kotlinx.serialization.SerialName" }

package io.natskt.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.FreeSpec

@Suppress("unused")
class SerialNamesTest :
	FreeSpec({
		"serial names must be snake case" {
			Konsist
				.scopeFromProject()
				.classes()
				.withoutWhitelistedClasses()
				.filter { klass ->
					klass.hasAnnotation { it.fullyQualifiedName == "kotlinx.serialization.SerialName" }
				}.flatMap { klass ->
					klass.properties()
				}.forEach { property ->
					System.err.println("doing $property")
					if (property.hasSerialNameAnnotation() && !property.text.contains("field")) {
						assert(
							property.name == property.name.toSnakeCase(),
						) {
							"${property.fullyQualifiedName} SerialName should be snake case"
						}
					}
				}
		}
	})

fun KoPropertyDeclaration.hasSerialNameAnnotation(): Boolean = hasAnnotation { it.fullyQualifiedName == "kotlinx.serialization.SerialName" }

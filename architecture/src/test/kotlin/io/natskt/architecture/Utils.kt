package io.natskt.architecture

import com.lemonappdev.konsist.api.declaration.KoClassDeclaration

fun String.toSnakeCase() =
	this
		.fold(StringBuilder()) { acc, c ->
			if (c.isUpperCase()) {
				acc.append("_${c.lowercaseChar()}")
			} else if (c.isDigit()) {
				acc.append("_$c")
			} else {
				acc.append(c)
			}
		}.toString()

fun String.isCamelCase() = this.any { it.isUpperCase() }

fun List<KoClassDeclaration>.withoutWhitelistedClasses() =
	filterNot {
		it.projectPath.startsWith("/.direnv")
	}.filterNot {
		it.projectPath.startsWith("/examples")
	}

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.spotless) apply false
}

allprojects {
    apply(plugin = "com.diffplug.spotless")
    val spotless = extensions.getByName("spotless") as SpotlessExtension
    spotless.apply {
        lineEndings = LineEnding.UNIX // configuration cache bug: https://github.com/diffplug/spotless/issues/2431
        kotlin {
            target("src/**/*.kt")
            ktlint(libs.versions.ktlint.get()).apply {
				setEditorConfigPath(rootDir.resolve(".editorconfig"))
			}
			trimTrailingWhitespace()
        }
    }
}

subprojects.forEach { p ->
    p.group = "io.natskt"
}
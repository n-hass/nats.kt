import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.spotless) apply false
	alias(libs.plugins.mavenPublish) apply false
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

val pluginId = libs.plugins.mavenPublish.get().pluginId

subprojects {
	group = "io.github.n-hass"
	version = properties["natskt.version"].toString()

	apply(plugin = pluginId)
	apply(plugin = "signing")

	extensions.getByType<MavenPublishBaseExtension>().apply {
		signAllPublications()

		pom {
			url = "https://github.com/n-hass/nats.kt"

			licenses {
				license {
					name = "The Apache License, Version 2.0"
					url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
				}
			}
			developers {
				developer {
					id = "n-hass"
					name = "n-hass"
					email = "nick@hassan.host"
				}
			}

			scm {
				connection = "scm:git:git://github.com/n-hass/nats.kt.git"
				developerConnection = "scm:git:ssh://github.com:n-hass/nats.kt.git"
				url = "https://github.com/n-hass/nats.kt"
			}

		}
	}

	if (properties["natskt.gpgsign"].toString().toBoolean()) {
		println("Build signing enabled")
		extensions.getByType<SigningExtension>().apply {
			useGpgCmd()
		}
	}
}

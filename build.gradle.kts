import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.internal.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

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

val mavenPublishId = libs.plugins.mavenPublish.get().pluginId
val kotlinMultiplatformId = libs.plugins.kotlin.multiplatform.get().pluginId

subprojects {
	group = "io.github.n-hass"
	version = properties["natskt.version"].toString()

	apply(plugin = mavenPublishId)
	apply(plugin = "signing")

	plugins.withId(kotlinMultiplatformId) {
		extensions.findByType<KotlinMultiplatformExtension>()?.apply {
			jvmToolchain(libs.versions.jdk.get().toInt())

			compilerOptions {
				languageVersion = KotlinVersion.fromVersion(libs.versions.kotlin.languageVersion.get())
				apiVersion = KotlinVersion.fromVersion(libs.versions.kotlin.apiVersion.get())
			}

			targets.withType<KotlinJvmTarget>().configureEach {
				val main by compilations.getting {
					compileTaskProvider.configure {
						compilerOptions {
							jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
						}
					}
				}
			}
		}
	}

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

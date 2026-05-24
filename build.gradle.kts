import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.net.URI

plugins {
    // kotlin.multiplatform is declared here so its types are on root's buildscript
    // classpath (used by the `subprojects { … }` block below). Subprojects apply it via
    // the `natskt.kmp` convention plugin in build-logic, not directly.
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.spotless) apply false
	alias(libs.plugins.mavenPublish) apply false
	alias(libs.plugins.dokka)
	id("natskt.harness")
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
val dokkaPluginId = libs.plugins.dokka.get().pluginId

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
				optIn.add(
					"kotlin.time.ExperimentalTime"
				)
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

		apply(plugin = dokkaPluginId)
		configure<DokkaExtension> {
			dokkaSourceSets.configureEach {
				jdkVersion.set(libs.versions.jdk.get().toInt())
				sourceLink {
					localDirectory.set(rootDir)
					remoteUrl.set(URI("https://github.com/n-hass/nats.kt/blob/main/"))
					remoteLineSuffix.set("#L")
				}
			}
		}
	}

	val doSign = properties["natskt.gpgsign"].toString().toBoolean()

	extensions.getByType<MavenPublishBaseExtension>().apply {
		if (doSign) {
			signAllPublications()
		}

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

	if (doSign) {
		println("Build signing enabled")
		extensions.getByType<SigningExtension>().apply {
			useGpgCmd()
		}
	}
}

// API reference (Dokka) — aggregated HTML for all published, public modules.
// Output lands under docs/api so MkDocs serves it as part of the site.
dependencies {
	dokka(projects.core)
	dokka(projects.core.common)
	dokka(projects.core.transportTcp)
	dokka(projects.core.transportWs)
	dokka(projects.jetstream)
	dokka(projects.nativeTls)
	dokka(projects.nkeys)
	dokka(projects.nuid)
}

dokka {
	moduleName.set("NATS.kt")
	dokkaPublications.html {
		outputDirectory.set(layout.projectDirectory.dir("docs/api"))
	}
}

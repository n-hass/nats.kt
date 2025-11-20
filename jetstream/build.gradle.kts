@file:OptIn(ExperimentalWasmDsl::class)

import java.time.Duration
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()

    jvm()
    js {
        browser()
        nodejs {
			testTask {
				useMocha {
					timeout = "15000"
				}
			}
		}
    }

    wasmJs {
        browser()
        nodejs {
			testTask {
				useKarma()
				timeout = Duration.ofSeconds(15)
			}
		}
    }

    iosArm64()
    iosSimulatorArm64()

    linuxX64()
    linuxArm64()

    macosArm64()

    sourceSets {
        commonMain.dependencies {
			implementation(projects.core)
			implementation(projects.core.common)
			implementation(projects.internal)
			implementation(libs.whyoleg.secureRandom)
			implementation(libs.kotlinx.coroutines.core)
			implementation(libs.kotlinx.serialization.core)
			implementation(libs.kotlinx.serialization.json)
			implementation(libs.ktor.http)
		}

		commonTest.dependencies {
			implementation(kotlin("test"))
			implementation(libs.kotlinx.coroutines.test)
			implementation(libs.kotest.assertions.core)
			implementation(libs.turbine)
			implementation(projects.testHarness)
		}

		jvmTest.dependencies {
			implementation(projects.testHarness)
			implementation("org.slf4j:slf4j-simple:2.0.17")
			implementation("io.nats:jnats:2.22.0")
		}
    }
}

mavenPublishing {
	coordinates(artifactId = "natskt-jetstream")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - JetStream"
		description = "JetStream extensions from NATS.kt core"
	}
}

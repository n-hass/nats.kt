@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
}

kotlin {
    explicitApi()

    jvm()
    js {
        browser()
        nodejs()
    }

    wasmJs {
        browser()
        nodejs()
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
		}

		jvmTest.dependencies {
			implementation(projects.architecture)
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

@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
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
        val commonMain by getting {
            dependencies {
                implementation(projects.core.common)
                implementation(libs.whyoleg.secureRandom)
                implementation(libs.kotlinx.coroutines.core)

                implementation(libs.ktor.io)
                implementation(libs.ktor.network)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.engine.cio)
            }
        }
    }
}

mavenPublishing {
	coordinates(artifactId = "natskt-transport-ws")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - WebSocket transport"
		description = "Part of natskt-core"
	}
}

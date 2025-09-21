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
                implementation(libs.whyoleg.secureRandom)
            }
        }
    }
}

mavenPublishing {
	coordinates(artifactId = "natskt-nuid")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - NUID"
		description = "Part of natskt-core"
	}
}

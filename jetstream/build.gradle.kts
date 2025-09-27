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
				implementation(projects.core)
                implementation(libs.whyoleg.secureRandom)
            }
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

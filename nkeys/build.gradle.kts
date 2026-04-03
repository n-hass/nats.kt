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
        commonMain.dependencies {
			implementation(libs.whyoleg.cryptography.core)
			implementation(projects.crypto)
		}

        commonTest.dependencies {
			implementation(kotlin("test"))
			implementation(libs.kotlinx.coroutines.test)
		}


		jvmTest.dependencies {
			implementation("io.nats:nkeys-java:2.1.1")
		}
    }
}

mavenPublishing {
	coordinates(artifactId = "natskt-nkeys")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - NKeys"
		description = "Part of natskt-core"
	}
}

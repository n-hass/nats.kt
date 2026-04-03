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
			api(projects.core)
			api(projects.jetstream)
			implementation(projects.crypto)
		}
	}
}

mavenPublishing {
	coordinates(artifactId = "natskt-platform")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - Platform Bundle"
		description = "Full set of core, jetstream and cryptography libraries for NATS.kt"
	}
}

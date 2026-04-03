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
			api(libs.whyoleg.cryptography.provider.optimal)
		}

		jvmMain.dependencies {
			api(libs.whyoleg.cryptography.provider.jdk.bc)
		}

		iosMain.dependencies {
			api(libs.whyoleg.cryptography.provider.cryptokit)
		}
	}
}

mavenPublishing {
	coordinates(artifactId = "natskt-crypto")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - Cryptography Providers"
		description = "Part of natskt-core"
	}
}

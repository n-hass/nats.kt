@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

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
			implementation(libs.kotlinx.coroutines.core)
			implementation(libs.kotlinx.serialization.json)
			implementation(libs.kotlinx.coroutines.test)
			implementation(libs.ktor.client.core)
			implementation(libs.ktor.client.content.negotiation)
			implementation(libs.ktor.serialization.kotlinx.json)
			implementation(projects.natskt.core)
		}

		commonTest.dependencies {
			implementation(kotlin("test"))
		}

		jvmMain.dependencies {
			implementation(libs.ktor.client.engine.cio)
		}

		jsMain.dependencies {
			implementation(libs.ktor.client.engine.js)
		}

		wasmJsMain.dependencies {
			implementation(libs.ktor.client.engine.js)
		}

		iosArm64Main.dependencies {
			implementation(libs.ktor.client.engine.darwin)
		}

		iosSimulatorArm64Main.dependencies {
			implementation(libs.ktor.client.engine.darwin)
		}

		macosArm64Main.dependencies {
			implementation(libs.ktor.client.engine.darwin)
		}

		linuxX64Main.dependencies {
			implementation(libs.ktor.client.engine.curl)
		}

		linuxArm64Main.dependencies {
			implementation(libs.ktor.client.engine.curl)
		}
	}
}

import org.gradle.kotlin.dsl.dependencies

plugins {
    kotlin("multiplatform") version libs.versions.kotlin.stdlib.get()
}

kotlin {
	sourceSets {
		val commonMain by getting
		val commonTest by getting

		nativeMain.dependencies {
			implementation("io.github.n-hass:core")
			implementation("io.github.n-hass:crypto")
			implementation("io.github.n-hass:native-tls")
			implementation(libs.kotlinx.coroutines.core)
			implementation(libs.ktor.client.core)
			implementation(libs.ktor.client.engine.curl)
			implementation(libs.ktor.client.websockets)
			implementation(libs.kotlinx.io.core)
		}
	}



	macosArm64 {
		binaries {
			executable {
				entryPoint = "io.natskt.main"
			}
		}
	}

	linuxArm64 {
		binaries {
			executable {
				entryPoint = "io.natskt.main"
			}
		}
	}
}


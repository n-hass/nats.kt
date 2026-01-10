plugins {
    kotlin("multiplatform") version libs.versions.kotlin.stdlib.get()
}

kotlin {
	sourceSets {
		val commonMain by getting
		val commonTest by getting
	}

	macosArm64 {
		binaries {
			executable {
				entryPoint = "io.natskt.main"
			}
		}
		// Optional: if you want to use -D flags or similar at runtime
		compilations["main"].defaultSourceSet {
			dependencies {
				implementation("io.github.n-hass:core")
				implementation(libs.kotlinx.coroutines.core)
				implementation(libs.ktor.client.core)
				implementation(libs.ktor.client.engine.cio)
				implementation(libs.ktor.client.websockets)
			}
		}
		compilations["test"].defaultSourceSet {
			dependencies {
			}
		}
	}
}


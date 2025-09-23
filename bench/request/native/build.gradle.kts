plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
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
				implementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")
				implementation(libs.ktor.client.websockets)
			}
		}
		compilations["test"].defaultSourceSet {
			dependencies {
			}
		}
	}
}

// (Optional) nice defaults for stricter code and consistent builds
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
	compilerOptions {
		allWarningsAsErrors.set(false)
		progressiveMode.set(true)
		// freeCompilerArgs.add("-Xexpect-actual-classes")
	}
}

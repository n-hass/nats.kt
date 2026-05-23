import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Standard nats.kt multiplatform target set: `jvm`, `js` and `wasmJs` (each with `browser`
 * and `nodejs`), Apple native (`iosArm64`, `iosSimulatorArm64`, `macosArm64`), and Linux
 * native (`linuxX64`, `linuxArm64`).
 *
 * Modules that need custom test-runner configuration (e.g. Karma/Mocha timeouts) can
 * re-enter the `js` / `wasmJs` blocks after this call — target getters are idempotent.
 */
@OptIn(ExperimentalWasmDsl::class)
fun KotlinMultiplatformExtension.allTargets() {
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
	macosArm64()

	linuxX64()
	linuxArm64()
}

import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
	alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
	applyDefaultHierarchyTemplate()

	jvm()

	iosArm64()
	iosSimulatorArm64()
	macosArm64()

	linuxX64()
	linuxArm64()

	sourceSets {
		val commonTest by getting

		val nativeAndJvmSharedTest by creating {
			dependsOn(commonTest)
		}

		jvmTest { dependsOn(nativeAndJvmSharedTest) }
		nativeTest { dependsOn(nativeAndJvmSharedTest) }

		commonTest.dependencies {
			implementation(kotlin("test"))
			implementation(projects.core)
			implementation(projects.testHarness)
			implementation(libs.kotlinx.coroutines.core)
			implementation(libs.kotlinx.coroutines.test)
			implementation(libs.ktor.client.engine.cio)
		}

		appleTest.dependencies {
			implementation(libs.ktor.client.engine.darwin)
		}

		jvmTest.dependencies {
			implementation(libs.ktor.client.engine.java)
			implementation(libs.ktor.client.engine.okhttp)
		}

		macosTest.dependencies {
			implementation(libs.ktor.client.engine.curl)
		}

		linuxTest.dependencies {
			implementation(libs.ktor.client.engine.curl)
		}

		nativeTest.dependencies {
			implementation(projects.nativeTls)
		}
	}
}

// Apple Simulator tests must run inside the simulator's launchd context so the test
// process can reach trustd/securityd. With `standalone = false`, the simulator must
// actually be booted before the test runs — boot it idempotently (no-op if already up).
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
	standalone.set(false)
	doFirst {
		val deviceName = device.get()
		val exit = ProcessBuilder("xcrun", "simctl", "bootstatus", deviceName, "-b")
			.inheritIO()
			.start()
			.waitFor()
		if (exit != 0) {
			throw GradleException("Failed to boot iOS Simulator '$deviceName' (exit=$exit)")
		}
	}
}

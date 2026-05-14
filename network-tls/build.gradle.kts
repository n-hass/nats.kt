import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

plugins {
	alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
	explicitApi()

	iosArm64()
	iosSimulatorArm64()

	linuxX64()
	linuxArm64()

	macosArm64()

	applyDefaultHierarchyTemplate()

	sourceSets {
		nativeMain.dependencies {
			implementation(libs.ktor.io)
			implementation(libs.ktor.network)
			implementation(libs.ktor.network.tls)
			implementation(libs.kotlinx.coroutines.core)
			implementation(libs.whyoleg.cryptography.core)
			implementation(libs.whyoleg.secureRandom)
		}

    appleMain.dependencies {
      implementation(libs.whyoleg.cryptography.provider.cryptokit)
    }

		linuxMain.dependencies {
			implementation(libs.whyoleg.cryptography.provider.optimal)
			implementation(libs.whyoleg.cryptography.provider.openssl3.api)
		}

		nativeTest.dependencies {
			implementation(kotlin("test"))
			implementation(libs.kotlinx.coroutines.test)
		}
	}

	targets.withType<KotlinNativeTarget>()
		.matching { it.konanTarget.family == Family.LINUX }
		.configureEach {
			compilations.getByName("main") {
				cinterops {
					val opensslX509 by creating {
						defFile(project.file("src/linuxMain/cinterop/openssl_x509.def"))
					}
				}
			}
		}
}

mavenPublishing {
	coordinates(artifactId = "natskt-network-tls")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin - Native TLS"
		description = "TLS 1.2/1.3 implementation for Kotlin/Native over Ktor sockets"
	}
}

// The TLS test suite reads its server ports from a TLS_TEST_PORTS_FILE env var. iOS Simulator
// runs tests in a sandboxed simctl process that does not inherit Gradle's environment, so the
// pointer to the shared test server doesn't reach the simulator. Skip the test suite there;
// macOS and Linux still provide native coverage for the same handshake code.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest>()
	.matching { it.name == "iosSimulatorArm64Test" }
	.configureEach { enabled = false }

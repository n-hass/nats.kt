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
			api(projects.core.common)
			implementation(libs.ktor.network)
			implementation(libs.kotlinx.coroutines.core)
		}
	}
}

mavenPublishing {
	coordinates(artifactId = "natskt-network-tls-spi")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin - Native TLS SPI"
		description = "Registration contract between :core:transport-tcp and :network-tls"
	}
}

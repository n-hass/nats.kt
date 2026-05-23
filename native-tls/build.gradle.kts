import com.ensody.nativebuilds.cinterops

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.nativebuilds)
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
			implementation(projects.nativeTlsSpi)
			implementation(libs.ktor.io)
			implementation(libs.ktor.network)
			implementation(libs.ktor.network.tls)
			implementation(libs.kotlinx.coroutines.core)
			implementation(libs.kotlinLogging)
		}

		nativeTest.dependencies {
			implementation(kotlin("test"))
			implementation(libs.kotlinx.coroutines.test)
		}
	}

	cinterops(libs.nativebuilds.openssl.headers) {
		definitionFile.set(file("src/nativeMain/cinterop/openssl.def"))
	}
}

mavenPublishing {
	coordinates(artifactId = "natskt-native-tls")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin - Native TLS"
		description = "TLS 1.2/1.3 over Ktor sockets, backed by OpenSSL with platform trust evaluation"
	}
}

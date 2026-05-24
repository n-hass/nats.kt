import org.jetbrains.kotlin.gradle.utils.toSetOrEmpty

plugins {
	alias(libs.plugins.natskt.kmp)
}

kotlin {
	explicitApi()
	allTargets()

	sourceSets {
		commonMain.dependencies {
			implementation(libs.whyoleg.cryptography.core)
		}
		jvmMain.dependencies {
			api(libs.whyoleg.cryptography.provider.jdk.bc)
		}
		appleMain.dependencies {
			api(libs.whyoleg.cryptography.provider.cryptokit)
		}
		linuxMain.dependencies {
			api(libs.whyoleg.cryptography.provider.openssl3.api)
		}
		jsMain.dependencies {
			api(libs.whyoleg.cryptography.provider.webcrypto)
		}
		wasmJsMain.dependencies {
			api(libs.whyoleg.cryptography.provider.webcrypto)
		}
	}
}

mavenPublishing {
	coordinates(artifactId = "natskt-crypto-headless")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - Cryptography Providers"
		description = "Part of natskt-core"
	}
}

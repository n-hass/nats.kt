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
			api(projects.cryptoHeadless)
		}
		linuxMain.dependencies {
			api(libs.whyoleg.cryptography.provider.openssl3.prebuilt.nativebuilds)
		}
	}
}

mavenPublishing {
	coordinates(artifactId = "natskt-crypto")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - Cryptography Providers"
		description = "Part of natskt-core"
	}
}

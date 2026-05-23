plugins {
	alias(libs.plugins.natskt.kmp)
}

kotlin {
	explicitApi()
	allTargets()

	sourceSets {
		commonMain.dependencies {
			api(libs.whyoleg.cryptography.provider.optimal)
		}

		jvmMain.dependencies {
			api(libs.whyoleg.cryptography.provider.jdk.bc)
		}

		iosMain.dependencies {
			api(libs.whyoleg.cryptography.provider.cryptokit)
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

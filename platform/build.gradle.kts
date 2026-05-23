plugins {
	alias(libs.plugins.natskt.kmp)
}

kotlin {
	explicitApi()
	allTargets()

	sourceSets {
		commonMain.dependencies {
			api(projects.core)
			api(projects.jetstream)
			implementation(projects.crypto)
		}
	}
}

mavenPublishing {
	coordinates(artifactId = "natskt-platform")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - Platform Bundle"
		description = "Full set of core, jetstream and cryptography libraries for NATS.kt"
	}
}

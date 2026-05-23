plugins {
    alias(libs.plugins.natskt.kmp)
}

kotlin {
    explicitApi()
    allTargets()

    sourceSets {
        commonMain.dependencies {
			implementation(libs.whyoleg.secureRandom)
		}
    }
}

mavenPublishing {
	coordinates(artifactId = "natskt-nuid")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - NUID"
		description = "Part of natskt-core"
	}
}

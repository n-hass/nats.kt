plugins {
    alias(libs.plugins.natskt.kmp)
}

kotlin {
    explicitApi()
    allTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
				api(libs.kotlinLogging)
                implementation(libs.kotlinx.coroutines.core)

                implementation(libs.ktor.io)
                implementation(libs.ktor.network)
                implementation(libs.ktor.http)
            }
        }
    }
}

mavenPublishing {
	coordinates(artifactId = "natskt-core-common")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - core shared models"
		description = "Part of natskt-core"
	}
}
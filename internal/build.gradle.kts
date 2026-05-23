import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING

plugins {
    alias(libs.plugins.natskt.kmp)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.buildkonfig)
}

kotlin {
    allTargets()

    sourceSets {
        val commonMain by getting {
            dependencies {
				api(projects.core.common)

				api(libs.kotlinLogging)
                implementation(libs.kotlinx.coroutines.core)
				implementation(libs.kotlinx.serialization.core)
				implementation(libs.kotlinx.serialization.json)

                implementation(libs.ktor.io)
                implementation(libs.ktor.network)
                implementation(libs.ktor.http)
            }
        }
    }
}

buildkonfig {
	packageName = "io.natskt.internal"

	defaultConfigs {
		buildConfigField(STRING, "version", properties["natskt.version"].toString(), const = true)
	}
}

mavenPublishing {
	coordinates(artifactId = "natskt-internal")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - internal shared models"
		description = "Part of natskt-core"
	}
}
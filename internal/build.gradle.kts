@file:OptIn(ExperimentalWasmDsl::class)

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.buildkonfig)
}

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }

    wasmJs {
        browser()
        nodejs()
    }

    iosArm64()
    iosSimulatorArm64()

    linuxX64()
    linuxArm64()

    macosArm64()

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
		buildConfigField(STRING, "version", project.version.toString(), const = true)
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
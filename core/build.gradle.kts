@file:OptIn(ExperimentalWasmDsl::class)

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    jvm()

    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    linuxX64()
    linuxArm64()

    js {
        browser {
			testTask {
				useKarma {
					useChromeHeadless()
				}
			}
		}
        nodejs()
    }
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting

        val nativeAndJvmSharedMain by creating {
            dependsOn(commonMain)
        }
        val nativeAndJvmSharedTest by creating {
            dependsOn(commonTest)
        }

        jvmMain { dependsOn(nativeAndJvmSharedMain) }
        jvmTest { dependsOn(nativeAndJvmSharedTest) }
        nativeMain { dependsOn(nativeAndJvmSharedMain) }
        nativeTest { dependsOn(nativeAndJvmSharedTest) }

        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.transportWs)
            api(projects.nuid)
            api(projects.nkeys)

            implementation(libs.whyoleg.secureRandom)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.io)
            implementation(libs.ktor.network)
            implementation(libs.ktor.http)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        nativeAndJvmSharedMain.dependencies {
            api(projects.core.transportTcp)
        }

        jsMain.dependencies {
            implementation(libs.ktor.client.engine.js)
        }

        wasmJsMain.dependencies {
            implementation(libs.ktor.client.engine.cio)
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
	coordinates(artifactId = "natskt-core")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client"
		description = "A Kotlin Multiplatform client for NATS messaging system"
	}
}

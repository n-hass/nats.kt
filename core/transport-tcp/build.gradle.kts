@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    explicitApi()

    jvm()

    js {
        nodejs()
    }

    wasmJs {
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
                implementation(projects.core.common)
                implementation(libs.whyoleg.secureRandom)
                implementation(libs.kotlinx.coroutines.core)

                implementation(libs.ktor.io)
                implementation(libs.ktor.network)
                implementation(libs.ktor.network.tls)
            }
        }
    }
}
rootProject.name = "natskt"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include("architecture")
include("core")
include("core:common")
include("core:transport-tcp")
include("core:transport-ws")
include("nuid")
include("nkeys")

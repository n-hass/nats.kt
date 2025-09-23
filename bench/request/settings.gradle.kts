enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("../../")

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
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

include("kotlin-jvm")
include("java")
include("native")


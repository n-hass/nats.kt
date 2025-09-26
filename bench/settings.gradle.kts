enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

includeBuild("../")

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
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

include("request:java")
include("request:kotlin-jvm")
include("request:native")

include("publish:java")
include("publish:kotlin-jvm")
include("publish:native")

include("subscribe:java")
include("subscribe:kotlin-jvm")
include("subscribe:native")

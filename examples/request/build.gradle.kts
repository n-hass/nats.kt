plugins {
    id("application")
    kotlin("jvm") version "2.1.0"
}

application {
    mainClass = "io.natskt.ApplicationKt"
}

dependencies {
	implementation("io.github.n-hass:core")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.engine.cio)
    implementation(libs.ktor.client.engine.okhttp)
    implementation(libs.ktor.client.websockets)
	implementation(libs.slf4j.simple)
}

tasks.withType<JavaExec>().configureEach {
	systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE")
}
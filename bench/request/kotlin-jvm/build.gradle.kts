plugins {
    id("application")
    kotlin("jvm") version libs.versions.kotlin.stdlib.get()
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
}

tasks.withType<JavaExec>().configureEach {
	systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "ERROR")
//	systemProperty("kotlinx.coroutines.debug", "off")
//	systemProperty("kotlinx.coroutines.stacktrace.recovery", "false")
}
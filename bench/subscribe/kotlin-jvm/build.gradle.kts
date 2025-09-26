plugins {
    id("application")
    kotlin("jvm") version libs.versions.kotlin.get()
}

application {
    mainClass = "io.natskt.ApplicationKt"
}

dependencies {
	implementation("io.github.n-hass:core")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.core)
    implementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-client-okhttp:${libs.versions.ktor.get()}")
    implementation(libs.ktor.client.websockets)
}

tasks.withType<JavaExec>().configureEach {
	systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "ERROR")
//	systemProperty("kotlinx.coroutines.debug", "off")
//	systemProperty("kotlinx.coroutines.stacktrace.recovery", "false")
}
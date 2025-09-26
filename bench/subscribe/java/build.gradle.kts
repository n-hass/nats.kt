plugins {
    id("application")
    kotlin("jvm") version libs.versions.kotlin.get()
}

application {
    mainClass = "io.natskt.ApplicationKt"
}

dependencies {
	implementation("io.nats:jnats:2.22.0")
	implementation(libs.kotlinx.coroutines.core)
}

tasks.withType<JavaExec>().configureEach {
	systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "ERROR")
}
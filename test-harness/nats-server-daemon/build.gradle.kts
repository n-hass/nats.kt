plugins {
	kotlin("jvm")
	alias(libs.plugins.kotlin.serialization)
	application
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(projects.testHarness)
	implementation(libs.kotlinx.coroutines.core)
	implementation(libs.kotlinx.serialization.json)
	implementation(libs.ktor.server.core)
	implementation(libs.ktor.server.callLogging)
	implementation(libs.ktor.server.cors)
	implementation(libs.ktor.server.engine.netty)
	implementation(libs.ktor.server.content.negotiation)
	implementation(libs.ktor.serialization.kotlinx.json)
	implementation(libs.slf4j.simple)
}

application {
	mainClass = "io.natskt.harness.daemon.MainKt"
}

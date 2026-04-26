plugins {
	kotlin("jvm")
	application
}

repositories {
	mavenCentral()
}

dependencies {
	implementation(libs.kotlinx.coroutines.core)
	implementation(libs.ktor.server.core)
	implementation(libs.ktor.server.engine.netty)
	implementation(libs.ktor.server.content.negotiation)
	implementation(libs.ktor.serialization.kotlinx.json)
	implementation(libs.slf4j.simple)
}

application {
	mainClass = "io.natskt.harness.tls.MainKt"
}

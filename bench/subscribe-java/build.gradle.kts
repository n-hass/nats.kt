plugins {
    id("application")
    kotlin("jvm") version libs.versions.kotlin.stdlib.get()
}

application {
    mainClass = "io.natskt.ApplicationKt"
}

dependencies {
    implementation("io.nats:jnats:2.21.4")
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "TRACE")
}

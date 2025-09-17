plugins {
    `jvm-test-suite`
    kotlin("jvm")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Trigger re-running architecture tests if any of the code changes.
    rootProject.subprojects.forEach { subproject ->
        // Access each source set in the subproject
        kotlin.runCatching {
            inputs.files(subproject.fileTree("src/"))
        }
    }
}

dependencies {
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.konsist)
}
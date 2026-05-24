plugins {
    alias(libs.plugins.natskt.kmp)
}

kotlin {
    explicitApi()
    allTargets()

    sourceSets {
        commonMain.dependencies {
			implementation(libs.whyoleg.cryptography.core)
		}

        commonTest.dependencies {
			implementation(kotlin("test"))
			implementation(libs.kotlinx.coroutines.test)
			implementation(projects.crypto)
		}

		nativeTest.dependencies {
			implementation(libs.nativebuilds.openssl.libcrypto)
		}

		jvmTest.dependencies {
			implementation("io.nats:nkeys-java:2.1.1")
		}
    }
}

mavenPublishing {
	coordinates(artifactId = "natskt-nkeys")
	publishToMavenCentral()

	pom {
		name = "NATS Kotlin Client - NKeys"
		description = "Part of natskt-core"
	}
}

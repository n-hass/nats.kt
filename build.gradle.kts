import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Task
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.util.Locale

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.spotless) apply false
	alias(libs.plugins.mavenPublish) apply false
}

private val isWindowsHost = System.getProperty("os.name").lowercase(Locale.US).contains("windows")
private val natsHarnessExecutable =
	layout.projectDirectory
		.dir("test-harness/nats-server-daemon/build/install/nats-server-daemon/bin")
		.file(if (isWindowsHost) "nats-server-daemon.bat" else "nats-server-daemon")

private val kotlinJsTestClass =
	runCatching { Class.forName("org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest") }.getOrNull()
private val kotlinWasmJsTestClass =
	runCatching { Class.forName("org.jetbrains.kotlin.gradle.targets.js.testing.KotlinWasmJsTest") }.getOrNull()
private val kotlinNativeTestClass =
	runCatching { Class.forName("org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest") }.getOrNull()

private val natsServerDaemonService =
	gradle.sharedServices.registerIfAbsent("natsServerDaemonService", NatsServerDaemonService::class) {
		parameters.executable.set(natsHarnessExecutable)
		parameters.args.set(emptyList())
		parameters.readyCheckUrl.set("http://127.0.0.1:4500/health")
		parameters.startupTimeoutSeconds.set(60)
		parameters.environment.putAll(
			mapOf(
				"NATS_HARNESS_HOST" to "127.0.0.1",
				"NATS_HARNESS_PORT" to "4500",
			),
		)
		maxParallelUsages.set(3)
	}

private val ensureNatsHarness =
	tasks.register<EnsureNatsHarnessTask>("ensureNatsHarness") {
		group = "verification"
		description = "Ensures the NATS test harness daemon is running before tests execute"
		dependsOn(natsHarnessInstallTaskPath)
		harnessService = natsServerDaemonService
		usesService(natsServerDaemonService)
	}
private val natsHarnessInstallTaskPath = ":test-harness:nats-server-daemon:installDist"

allprojects {
    apply(plugin = "com.diffplug.spotless")
    val spotless = extensions.getByName("spotless") as SpotlessExtension
    spotless.apply {
        lineEndings = LineEnding.UNIX // configuration cache bug: https://github.com/diffplug/spotless/issues/2431
        kotlin {
            target("src/**/*.kt")
            ktlint(libs.versions.ktlint.get()).apply {
				setEditorConfigPath(rootDir.resolve(".editorconfig"))
			}
			trimTrailingWhitespace()
        }
    }
}

val mavenPublishId = libs.plugins.mavenPublish.get().pluginId
val kotlinMultiplatformId = libs.plugins.kotlin.multiplatform.get().pluginId

subprojects {
	group = "io.github.n-hass"
	version = properties["natskt.version"].toString()

	apply(plugin = mavenPublishId)
	apply(plugin = "signing")

	plugins.withId(kotlinMultiplatformId) {
		extensions.findByType<KotlinMultiplatformExtension>()?.apply {
			jvmToolchain(libs.versions.jdk.get().toInt())

			compilerOptions {
				languageVersion = KotlinVersion.fromVersion(libs.versions.kotlin.languageVersion.get())
				apiVersion = KotlinVersion.fromVersion(libs.versions.kotlin.apiVersion.get())
			}

			targets.withType<KotlinJvmTarget>().configureEach {
				val main by compilations.getting {
					compileTaskProvider.configure {
						compilerOptions {
							jvmTarget = JvmTarget.fromTarget(libs.versions.jvmTarget.get())
						}
					}
				}
			}
		}
	}

	extensions.getByType<MavenPublishBaseExtension>().apply {
		signAllPublications()

		pom {
			url = "https://github.com/n-hass/nats.kt"

			licenses {
				license {
					name = "The Apache License, Version 2.0"
					url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
				}
			}
			developers {
				developer {
					id = "n-hass"
					name = "n-hass"
					email = "nick@hassan.host"
				}
			}

			scm {
				connection = "scm:git:git://github.com/n-hass/nats.kt.git"
				developerConnection = "scm:git:ssh://github.com:n-hass/nats.kt.git"
				url = "https://github.com/n-hass/nats.kt"
			}

		}
	}

	if (properties["natskt.gpgsign"].toString().toBoolean()) {
		println("Build signing enabled")
		extensions.getByType<SigningExtension>().apply {
			useGpgCmd()
		}
	}

	tasks.configureEach {
		if (!requiresNatsHarness(this, kotlinJsTestClass, kotlinWasmJsTestClass, kotlinNativeTestClass)) {
			return@configureEach
		}
		dependsOn(ensureNatsHarness)
		usesService(natsServerDaemonService)
	}
}

private fun requiresNatsHarness(
	task: Task,
	jsTestClass: Class<*>?,
	wasmJsTestClass: Class<*>?,
	nativeTestClass: Class<*>?,
): Boolean {
	if (task is Test) {
		return true
	}
	if (jsTestClass?.isInstance(task) == true) {
		return true
	}
	if (wasmJsTestClass?.isInstance(task) == true) {
		return true
	}
	if (nativeTestClass?.isInstance(task) == true) {
		return true
	}
	return false
}

/*
 * Convention: NATS test-harness wiring.
 *
 * Registers two long-running daemon BuildServices (`natsServerDaemonService` for the main
 * NATS harness, `tlsTestServerService` for the raw TLS test server) and the
 * `ensureNatsHarness` / `ensureTlsTestServer` tasks that gate test execution on them.
 * Subprojects' test tasks (JVM, JS, WasmJS, native) are automatically wired to depend on
 * the relevant ensure-task.
 *
 * Apply to the root project: `plugins { id("natskt.harness") }`.
 */

import java.util.Locale

val isWindowsHost = System.getProperty("os.name").lowercase(Locale.US).contains("windows")

val natsHarnessExecutable =
	layout.projectDirectory
		.dir("test-harness/nats-server-daemon/build/install/nats-server-daemon/bin")
		.file(if (isWindowsHost) "nats-server-daemon.bat" else "nats-server-daemon")

val kotlinJsTestClass =
	runCatching { Class.forName("org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest") }.getOrNull()
val kotlinWasmJsTestClass =
	runCatching { Class.forName("org.jetbrains.kotlin.gradle.targets.js.testing.KotlinWasmJsTest") }.getOrNull()
val kotlinNativeTestClass =
	runCatching { Class.forName("org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest") }.getOrNull()

val natsServerDaemonService =
	gradle.sharedServices.registerIfAbsent("natsServerDaemonService", NatsServerDaemonService::class) {
		parameters.executable.set(natsHarnessExecutable)
		parameters.workingDirectory.set(project(":test-harness:nats-server-daemon").layout.projectDirectory.asFile.toString())
		parameters.args.set(emptyList())
		parameters.readyCheckUrl.set("http://127.0.0.1:4500/health")
		parameters.startupTimeoutSeconds.set(60)
		parameters.environment.putAll(
			mapOf(
				"NATS_HARNESS_HOST" to "127.0.0.1",
				"NATS_HARNESS_PORT" to "4500",
			),
		)
		maxParallelUsages.set(
			properties["natskt.test.parallel"]?.toString()?.toInt() ?: 2,
		)
	}

val natsHarnessInstallTaskPath = ":test-harness:nats-server-daemon:installDist"

val ensureNatsHarness =
	tasks.register<EnsureNatsHarnessTask>("ensureNatsHarness") {
		group = "verification"
		description = "Ensures the NATS test harness daemon is running before tests execute"
		dependsOn(natsHarnessInstallTaskPath)
		harnessService = natsServerDaemonService
		usesService(natsServerDaemonService)
	}

// --- TLS Test Server ---

val tlsTestServerExecutable =
	layout.projectDirectory
		.dir("test-harness/tls-test-server/build/install/tls-test-server/bin")
		.file(if (isWindowsHost) "tls-test-server.bat" else "tls-test-server")

val tlsTestServerService =
	gradle.sharedServices.registerIfAbsent("tlsTestServerService", NatsServerDaemonService::class) {
		parameters.executable.set(tlsTestServerExecutable)
		parameters.workingDirectory.set(project(":test-harness:tls-test-server").layout.projectDirectory.asFile.toString())
		parameters.args.set(emptyList())
		parameters.readyCheckUrl.set("http://127.0.0.1:4501/health")
		parameters.startupTimeoutSeconds.set(30)
		parameters.environment.putAll(
			mapOf(
				"TLS_TEST_SERVER_HOST" to "127.0.0.1",
				"TLS_TEST_SERVER_PORT" to "4501",
			),
		)
		maxParallelUsages.set(1)
	}

val tlsTestServerInstallTaskPath = ":test-harness:tls-test-server:installDist"

val ensureTlsTestServer =
	tasks.register<EnsureNatsHarnessTask>("ensureTlsTestServer") {
		group = "verification"
		description = "Ensures the TLS test server is running before native-tls tests execute"
		dependsOn(tlsTestServerInstallTaskPath)
		harnessService = tlsTestServerService
		usesService(tlsTestServerService)
	}

subprojects {
	tasks.configureEach {
		if (!requiresNatsHarness(this, kotlinJsTestClass, kotlinWasmJsTestClass, kotlinNativeTestClass)) {
			return@configureEach
		}
		dependsOn(ensureNatsHarness)
		usesService(natsServerDaemonService)
	}

	// Wire native-tls native tests to the TLS test server
	if (path == ":native-tls") {
		tasks.configureEach {
			if (kotlinNativeTestClass?.isInstance(this) != true) return@configureEach
			dependsOn(ensureTlsTestServer)
			usesService(tlsTestServerService)
			val task = this as org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
			val portsPath = rootProject.file("test-harness/tls-test-server/ports.properties").absolutePath
			task.environment("TLS_TEST_PORTS_FILE", portsPath)
			// iOS Simulator tests run via `xcrun simctl spawn`, which does not forward the
			// launching process's environment into the simulated process. The simctl-honored
			// way to propagate a var is to set it on the parent with a SIMCTL_CHILD_ prefix;
			// the prefix is stripped when the child is launched.
			task.environment("SIMCTL_CHILD_TLS_TEST_PORTS_FILE", portsPath)
		}
	}
}

fun requiresNatsHarness(
	task: Task,
	jsTestClass: Class<*>?,
	wasmJsTestClass: Class<*>?,
	nativeTestClass: Class<*>?,
): Boolean {
	if (task is Test) return true
	if (jsTestClass?.isInstance(task) == true) return true
	if (wasmJsTestClass?.isInstance(task) == true) return true
	if (nativeTestClass?.isInstance(task) == true) return true
	return false
}

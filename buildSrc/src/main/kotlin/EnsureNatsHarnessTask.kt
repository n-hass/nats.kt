import org.gradle.api.DefaultTask
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class EnsureNatsHarnessTask : DefaultTask() {
	@get:Internal
	lateinit var harnessService: Provider<NatsServerDaemonService>

	@TaskAction
	fun ensureHarnessRunning() {
		harnessService.get()
	}
}

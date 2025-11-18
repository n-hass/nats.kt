package harness

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

public suspend fun waitForLog(
	server: RemoteNatsServer,
	predicate: (String) -> Boolean,
) {
	var offset = 0
	withTimeout(5_000) {
		while (true) {
			val response = server.fetchLogs(offset)
			for (line in response.entries) {
				if (predicate(line)) {
					return@withTimeout
				}
			}
			offset = response.nextOffset
			delay(50)
		}
	}
}

package io.natskt.internal

import io.natskt.api.Subject
import io.natskt.api.from
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestSubscriptionImplTest {
	@Test
	fun `emit completes deferred response`() =
		runTest {
			val subscription = RequestSubscriptionImpl(sid = "1")
			val message =
				OutgoingMessage(
					subject = Subject.from("reply"),
					replyTo = null,
					headers = null,
					data = byteArrayOf(1, 2, 3),
				)

			subscription.emit(message)

			assertEquals(message, subscription.response.await())
		}
}

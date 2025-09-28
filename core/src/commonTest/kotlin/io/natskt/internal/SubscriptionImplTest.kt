@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.natskt.internal

import app.cash.turbine.test
import io.natskt.api.Message
import io.natskt.api.Subject
import io.natskt.api.from
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun newMessage(payload: String): Message =
	OutgoingMessage(
		subject = Subject.from("demo"),
		replyTo = null,
		headers = null,
		data = payload.encodeToByteArray(),
	)

class SubscriptionImplTest {
	@Test
	fun `eager subscription activates immediately and replays`() =
		runTest {
			val starts = mutableListOf<Pair<String, InternalSubscriptionHandler>>()
			val stops = mutableListOf<String>()

			val subscription =
				SubscriptionImpl(
					subject = Subject.from("demo"),
					queueGroup = "group",
					sid = "1",
					scope = this,
					onStart =
						{ sub, sid, _, _ ->
							starts += sid to sub
						},
					onStop =
						{ sid, _ ->
							stops += sid
						},
					eagerSubscribe = true,
					unsubscribeOnLastCollector = false,
					prefetchReplay = 1,
				)

			advanceUntilIdle()

			assertEquals(1, starts.size)
			assertEquals("1", starts.single().first)
			assertSame(subscription, starts.single().second)
			assertTrue(subscription.isActive.value)

			subscription.emit(newMessage("payload"))

			subscription.messages.test {
				assertEquals("payload", awaitItem().data!!.decodeToString())
				cancelAndConsumeRemainingEvents()
			}

			subscription.unsubscribe()
			advanceUntilIdle()

			assertEquals(listOf("1"), stops)
			assertFalse(subscription.isActive.value)
		}

	@Test
	fun `subscription stops after last collector with debounce`() =
		runTest {
			val starts = mutableListOf<String>()
			val stops = mutableListOf<String>()

			val subscription =
				SubscriptionImpl(
					subject = Subject.from("demo"),
					queueGroup = null,
					sid = "2",
					scope = this,
					onStart =
						{ _, sid, _, _ ->
							starts += sid
						},
					onStop =
						{ sid, _ ->
							stops += sid
						},
					eagerSubscribe = false,
					unsubscribeOnLastCollector = true,
					prefetchReplay = 0,
				)

			val collectionJob = launch { subscription.messages.collect { /* drain */ } }
			advanceUntilIdle()

			assertEquals(listOf("2"), starts)
			assertTrue(subscription.isActive.value)

			collectionJob.cancel()
			advanceTimeBy(500)
			advanceUntilIdle()

			assertEquals(listOf("2"), stops)
			assertFalse(subscription.isActive.value)
		}
}

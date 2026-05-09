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
import kotlin.test.assertFailsWith
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

	@Test
	fun `unsubscribe with after issues UNSUB with max_msgs and tears down on count exhaustion`() =
		runTest {
			val stops = mutableListOf<Pair<String, Int?>>()

			val subscription =
				SubscriptionImpl(
					subject = Subject.from("demo"),
					queueGroup = null,
					sid = "auto",
					scope = this,
					onStart = { _, _, _, _ -> },
					onStop =
						{ sid, maxMsgs ->
							stops += sid to maxMsgs
						},
					eagerSubscribe = true,
					unsubscribeOnLastCollector = false,
					prefetchReplay = 0,
				)

			advanceUntilIdle()

			subscription.unsubscribe(after = 3)
			advanceUntilIdle()

			assertEquals(listOf<Pair<String, Int?>>("auto" to 3), stops)
			assertTrue(subscription.isActive.value)

			repeat(3) { subscription.emit(newMessage("payload-$it")) }
			advanceUntilIdle()

			assertEquals(listOf<Pair<String, Int?>>("auto" to 3, "auto" to null), stops)
			assertFalse(subscription.isActive.value)
		}

	@Test
	fun `unsubscribe with non-positive after rejects`() =
		runTest {
			val subscription =
				SubscriptionImpl(
					subject = Subject.from("demo"),
					queueGroup = null,
					sid = "rej",
					scope = this,
					onStart = { _, _, _, _ -> },
					onStop = { _, _ -> },
					eagerSubscribe = true,
					unsubscribeOnLastCollector = false,
					prefetchReplay = 0,
				)

			advanceUntilIdle()

			assertFailsWith<IllegalArgumentException> {
				subscription.unsubscribe(after = 0)
			}

			subscription.unsubscribe()
			advanceUntilIdle()
		}
}

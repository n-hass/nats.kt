package io.natskt.jetstream

import io.natskt.api.Subject
import io.natskt.api.fullyQualified
import io.natskt.api.internal.InternalNatsApi
import io.natskt.jetstream.internal.parseAckMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
v0 <prefix>.ACK.<stream name>.<consumer name>.<num delivered>.<stream sequence>.<consumer sequence>.<timestamp>
v1 <prefix>.ACK.<stream name>.<consumer name>.<num delivered>.<stream sequence>.<consumer sequence>.<timestamp>.<num pending>
v2 <prefix>.ACK.<domain>.<account hash>.<stream name>.<consumer name>.<num delivered>.<stream sequence>.<consumer sequence>.<timestamp>.<num pending>
 */
@OptIn(ExperimentalTime::class, InternalNatsApi::class)
class AckMetadataParseTest {
	@Test
	fun v0AckWithoutPendingCannotBeParsed() {
		val subject = Subject.fullyQualified("\$JS.ACK.orders.consumer.9.123.5.1700000000000000000")

		assertNull(parseAckMetadata(subject))
	}

	@Test
	fun v1AckWithPendingProducesMetadata() {
		val timestamp = 1_700_000_000_000_000_000L
		val subject = Subject.fullyQualified("\$JS.ACK.orders.consumer.9.77.50.$timestamp.3")
		val metadata = parseAckMetadata(subject)

		assertNotNull(metadata)
		assertEquals(77UL, metadata.streamSequence)
		assertEquals(3UL, metadata.pending)
		assertEquals(Instant.fromEpochMilliseconds(timestamp / 1_000_000), metadata.timestamp)
	}

	@Test
	fun v2AckWithDomainInformationProducesMetadata() {
		val timestamp = 1_700_000_000_123_456_789L
		val expectedTimestamp =
			Instant
				.fromEpochMilliseconds(timestamp / 1_000_000)
				.plus((timestamp % 1_000_000).nanoseconds)
		val subject = Subject.fullyQualified("\$JS.ACK._.ACC123.orders.consumer.11.88.60.$timestamp.12")
		val metadata = parseAckMetadata(subject)

		assertNotNull(metadata)
		assertEquals(88UL, metadata.streamSequence)
		assertEquals(12UL, metadata.pending)
		assertEquals(expectedTimestamp, metadata.timestamp)
	}
}

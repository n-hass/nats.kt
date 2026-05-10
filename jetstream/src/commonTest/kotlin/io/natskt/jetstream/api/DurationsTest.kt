package io.natskt.jetstream.api

import io.natskt.jetstream.api.internal.toGoDurationString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class DurationsTest {
	@Test
	fun `whole units emit a single suffix`() {
		assertEquals("5s", 5.seconds.toGoDurationString())
		assertEquals("1m", 1.minutes.toGoDurationString())
		assertEquals("250ms", 250.milliseconds.toGoDurationString())
		assertEquals("500us", 500.microseconds.toGoDurationString())
		assertEquals("42ns", 42.nanoseconds.toGoDurationString())
	}

	@Test
	fun `compound durations are emitted without whitespace`() {
		// Go's time.ParseDuration rejects whitespace, so the formatter must produce a
		// concatenated form even for compound durations.
		val compound = (1.hours + 30.minutes).toGoDurationString()
		assertFalse(compound.contains(' '), "got: $compound")
		assertEquals("1h30m", compound)
		assertEquals("1m30s", 90.seconds.toGoDurationString())
	}

	@Test
	fun `zero is rendered as 0s`() {
		assertEquals("0s", Duration.ZERO.toGoDurationString())
	}

	@Test
	fun `fractional seconds use the sub-second remainder, not totalNs`() {
		// Earlier versions used totalNs (whole + frac) to build the decimal part, which produced
		// "1.15s" for 1.5 seconds because trimEnd('0') chewed into the seconds digit.
		assertEquals("1.5s", (1.seconds + 500.milliseconds).toGoDurationString())
		assertEquals("2.25s", (2.seconds + 250.milliseconds).toGoDurationString())
		assertEquals("1.000005s", (1.seconds + 5.microseconds).toGoDurationString())
	}
}

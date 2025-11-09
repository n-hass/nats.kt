package io.natskt.jetstream.api.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Serializer for Kotlin Duration <-> Go time.Duration textual format.
 * Compatible with Go's encoding/json: e.g. "1h2m3.5s", "500ms", "250ns", "-1.25us".
 * Serializes to concise Go-style mixed-unit strings ("1s", "1.5s", "2m3s", etc.).
 */
internal object GoDurationStringSerializer : KSerializer<Duration> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("GoDuration", PrimitiveKind.STRING)

	override fun serialize(
		encoder: Encoder,
		value: Duration,
	) {
		encoder.encodeString(formatGoStyle(value))
	}

	override fun deserialize(decoder: Decoder): Duration {
		val s = decoder.decodeString().trim()
		if (s.isEmpty()) return Duration.ZERO
		return parseGoDuration(s)
	}

	private fun parseGoDuration(text: String): Duration {
		var i = 0
		val n = text.length

		fun peek(): Char? = if (i < n) text[i] else null

		fun consume(c: Char): Boolean =
			if (peek() == c) {
				i++
				true
			} else {
				false
			}

		fun advanceBy(k: Int) {
			i += k
		}

		var negative = false
		if (consume('-')) negative = true else consume('+')

		if (i >= n) error("invalid duration: empty after sign")

		var totalNs = 0.0

		while (i < n) {
			val startNum = i
			var hasDigits = false
			while (peek()?.isDigit() == true) {
				i++
				hasDigits = true
			}
			if (peek() == '.') {
				i++
				while (peek()?.isDigit() == true) {
					i++
					hasDigits = true
				}
			}
			if (!hasDigits) {
				error("invalid duration: expected number at pos $startNum")
			}

			val numStr = text.substring(startNum, i)
			val value =
				numStr.toDoubleOrNull()
					?: error("invalid duration: bad number '$numStr'")

			val unitStart = i
			val unit =
				when {
					text.regionMatches(i, "h", 0, 1) -> {
						advanceBy(1)
						"h"
					}
					text.regionMatches(i, "m", 0, 1) && !text.regionMatches(i, "ms", 0, 2)
					-> {
						advanceBy(1)
						"m"
					} // minutes, not ms
					text.regionMatches(i, "s", 0, 1) -> {
						advanceBy(1)
						"s"
					}
					text.regionMatches(i, "ms", 0, 2) -> {
						advanceBy(2)
						"ms"
					}
					text.regionMatches(i, "us", 0, 2) -> {
						advanceBy(2)
						"us"
					}
					text.regionMatches(i, "µs", 0, 2) -> {
						advanceBy(2)
						"µs"
					}
					text.regionMatches(i, "ns", 0, 2) -> {
						advanceBy(2)
						"ns"
					}
					else -> error("invalid duration: expected unit at pos $unitStart")
				}

			val unitNs =
				when (unit) {
					"h" -> 3_600_000_000_000.0
					"m" -> 60_000_000_000.0
					"s" -> 1_000_000_000.0
					"ms" -> 1_000_000.0
					"us", "µs" -> 1_000.0
					"ns" -> 1.0
					else -> error("unknown unit $unit")
				}

			totalNs += value * unitNs
		}

		val signed = if (negative) -totalNs else totalNs
		return signed.nanoseconds
	}

	// ---- Formatting ----

	private fun formatGoStyle(d: Duration): String {
		if (d.isInfinite()) error("cannot encode infinite Duration")
		var ns = d.inWholeNanoseconds
		if (ns == 0L) return "0s"

		val neg = ns < 0
		if (neg) ns = -ns

		val sb = StringBuilder()
		if (neg) sb.append('-')

		// Units: h, m, s, ms, µs, ns (smallest nonzero unit wins)
		val hours = ns / 3_600_000_000_000L
		ns %= 3_600_000_000_000L
		val mins = ns / 60_000_000_000L
		ns %= 60_000_000_000L
		val secs = ns / 1_000_000_000L
		ns %= 1_000_000_000L
		val ms = ns / 1_000_000L
		ns %= 1_000_000L
		val us = ns / 1_000L
		ns %= 1_000L
		val nsec = ns

		if (hours != 0L) sb.append(hours).append('h')
		if (mins != 0L) sb.append(mins).append('m')

		when {
			// prefer fractional seconds if sub-second but >1s precision
			(secs != 0L && (ms != 0L || us != 0L || nsec != 0L)) -> {
				val totalNs = (
					secs * 1_000_000_000L +
						ms * 1_000_000L +
						us * 1_000L + nsec
				)
				val frac = totalNs.toString().padStart(9, '0').trimEnd('0')
				sb.append("$secs.${frac}s")
			}
			secs != 0L -> sb.append(secs).append('s')
			ms != 0L -> sb.append(ms).append("ms")
			us != 0L -> sb.append(us).append("us")
			nsec != 0L -> sb.append(nsec).append("ns")
		}

		return sb.toString()
	}
}

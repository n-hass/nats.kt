package io.natskt.internal

import io.natskt.api.MessageHeaders

fun parseHeaders(s: String): MessageHeaders? {
	require(s.startsWith(HEADER_START)) { "invalid NATS header preamble" }
	// Find the first CRLF to handle optional status codes after NATS/1.0
	val firstCrlf = s.indexOf(LINE_END)
	require(firstCrlf >= HEADER_START.length) { "invalid NATS header preamble" }
	val start = firstCrlf + LINE_END.length

	// Check if there are any headers after the status line
	// If the remaining content after the first line is just CRLF, there are no headers
	if (start >= s.length || s.substring(start) == LINE_END) {
		return null
	}

	val end = s.lastIndexOf(DOUBLE_LINE_END).takeIf { it >= start } ?: error("headers missing terminating CRLF CRLF")
	val map = LinkedHashMap<String, MutableList<String>>()
	var i = start
	while (i < end) {
		val j = s.indexOf('\r', i)
		val lineEnd = if (j in i until end && j + 1 <= end && s.getOrNull(j + 1) == '\n') j else end
		if (lineEnd <= i) break // empty line (should not happen before end)
		val line = s.substring(i, lineEnd)
		val c = line.indexOf(':')
		require(c > 0) { "malformed header line: '$line'" }
		val name = line.take(c) // preserve case
		val value = line.substring(c + 1).trimStart() // strip leading space after colon
		if (map[name] == null) {
			map[name] = mutableListOf()
		}
		map[name]?.add(value)
		i = lineEnd + 2 // skip CRLF
	}
	return map.ifEmpty { null }
}

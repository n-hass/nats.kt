package io.natskt.jetstream.api

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

public data class PublishOptions(
	public val ttl: Duration? = null,
	public val id: String? = null,
	public val expectedLastId: String? = null,
	public val expectedStream: String? = null,
	public val expectedLastSequence: ULong? = null,
	public val expectedLastSubjectSequence: ULong? = null,
	public val retryWait: Duration = 250.milliseconds,
	public val retryAttempts: Int = 2,
)

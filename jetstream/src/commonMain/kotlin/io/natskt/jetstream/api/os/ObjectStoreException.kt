package io.natskt.jetstream.api.os

import io.natskt.api.NatsClientException

/**
 * Errors surfaced by Object Store client logic before any server response
 * (or after the response when validation fails). Server-side JetStream
 * errors continue to surface as `JetStreamApiException`.
 */
public sealed class ObjectStoreException(
	message: String,
) : NatsClientException(message)

public class ObjectNotFound(
	name: String,
) : ObjectStoreException("object not found: $name")

public class ObjectIsDeleted(
	name: String,
) : ObjectStoreException("object is deleted: $name")

public class ObjectAlreadyExists(
	name: String,
) : ObjectStoreException("object already exists: $name")

public class LinkNotAllowedOnPut : ObjectStoreException("links cannot be supplied to put; use addLink instead")

public class CantLinkToLink : ObjectStoreException("cannot create a link that points at another link")

public class GetLinkToBucket : ObjectStoreException("cannot get the contents of a bucket-only link")

public class GetChunksMismatch(
	public val expected: Long,
	public val actual: Long,
) : ObjectStoreException("chunk count mismatch: expected $expected, received $actual")

public class GetSizeMismatch(
	public val expected: Long,
	public val actual: Long,
) : ObjectStoreException("size mismatch: expected $expected bytes, received $actual")

public class GetDigestMismatch(
	public val expected: String?,
	public val actual: String,
) : ObjectStoreException("digest mismatch: expected $expected, computed $actual")

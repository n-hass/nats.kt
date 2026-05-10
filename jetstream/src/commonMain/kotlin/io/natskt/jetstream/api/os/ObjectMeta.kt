package io.natskt.jetstream.api.os

/**
 * The user-supplied input to [ObjectStoreBucket.put]. Holds the object name
 * and any optional descriptive data: a free-form description, request headers
 * to attach to the meta message, arbitrary metadata, and per-object options
 * such as a chunk size override or an [ObjectLink].
 */
public data class ObjectMeta(
	public val name: String,
	public val description: String? = null,
	public val headers: Map<String, List<String>> = emptyMap(),
	public val metadata: Map<String, String> = emptyMap(),
	public val options: ObjectMetaOptions? = null,
) {
	public companion object {
		public fun objectName(name: String): ObjectMeta = ObjectMeta(name = name)
	}
}

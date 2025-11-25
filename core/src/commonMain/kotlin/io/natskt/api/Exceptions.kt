package io.natskt.api

public open class NatsClientException(
	message: String? = null,
	cause: Throwable? = null,
) : RuntimeException(message, cause)

public class ConnectionClosedException(
	message: String? = null,
	cause: Throwable? = null,
) : NatsClientException(message, cause)

public class ConfigurationException(
	message: String? = null,
	cause: Throwable? = null,
) : NatsClientException(message, cause)

public class ProtocolException(
	message: String? = null,
	cause: Throwable? = null,
) : NatsClientException(message, cause)

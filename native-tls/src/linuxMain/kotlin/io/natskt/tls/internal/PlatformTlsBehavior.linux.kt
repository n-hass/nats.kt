package io.natskt.tls.internal

// Linux uses SSL_set_fd to wrap the existing socket in place.
internal actual val platformTlsUpgradeReopens: Boolean = false

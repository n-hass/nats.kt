package io.natskt.tls.internal

/**
 * `true` if the platform's TLS upgrade closes the original socket and reconnects, so the server
 * will send a fresh INFO frame the engine must consume. See
 * [io.natskt.client.transport.Transport.tlsUpgradeReopened].
 */
internal expect val platformTlsUpgradeReopens: Boolean

package io.natskt.tls.internal

// Network.framework cannot adopt an existing connected fd; the upgrader closes the plaintext
// connection and opens a fresh nw_connection_t with TLS parameters. ProtocolEngineImpl will
// re-read INFO from the new connection before sending CONNECT.
internal actual val platformTlsUpgradeReopens: Boolean = true

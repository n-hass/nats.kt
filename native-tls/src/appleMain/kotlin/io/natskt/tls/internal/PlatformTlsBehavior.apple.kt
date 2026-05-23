package io.natskt.tls.internal

// Network.framework cannot adopt an existing connected fd; the upgrader closes the plaintext
// connection and opens a fresh nw_connection_t with TLS parameters. Today the value is `false`
// while the legacy hand-rolled TLS path is still in place; it will flip to `true` once the
// Network.framework path is implemented
internal actual val platformTlsUpgradeReopens: Boolean = false

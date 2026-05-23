# native-tls fix list

Audit findings against RFC 8446 / RFC 5246 / RFC 5280, cross-checked with rustls (`/Users/nick/Projects/rustls`).

## P0 — Security

### 1. Apple custom-anchor validator skips RFC 5280 constraints
**Issue:** `CertValidator.apple.kt:57-87` walks the chain with `SecKeyVerifySignature` + time-window only. No BasicConstraints `cA=TRUE`, no `pathLenConstraint`, no KeyUsage, no EKU `serverAuth`, no critical-extension reject. A non-CA leaf cert signed by the trusted anchor can impersonate any host.

**Fix:** Either route through `SecTrustSetAnchorCertificates` on real Apple OSes (keep the manual walk only as a Simulator fallback), or add the missing checks: parse the `extensions` SEQUENCE; for every issuer assert `basicConstraints.cA == TRUE` and `keyUsage.keyCertSign`; reject unrecognized critical extensions; verify the leaf has EKU `1.3.6.1.5.5.7.3.1`.

**Ground truth:** RFC 5280 §6.1.4(k): *"If certificate i is a version 3 certificate, verify that the basicConstraints extension is present and that cA is set to TRUE."* §4.2: *"A certificate-using system MUST reject the certificate if it encounters a critical extension it does not recognize."*

### 2. EC public key not validated as on-curve
**Issue:** `TlsHandshake.kt:438` (TLS 1.3 key_share), `:700` (TLS 1.2 ECDHE), and `:606` / `:834` (ECDSA verify paths) decode server EC points via `ecdh.publicKeyDecoder(...).decodeFromByteArrayBlocking(EC.PublicKey.Format.RAW, ...)` and trust the result. If `dev.whyoleg.cryptography` does not verify the point lies on the curve and is not the identity, invalid-curve attacks recover the client's private scalar.

**Fix:** Confirm the provider validates points. If it doesn't, add an explicit check before deriving the shared secret: parse the uncompressed point, verify `0x04` prefix, length matches curve, `(x,y)` satisfies `y² ≡ x³ + ax + b (mod p)`, and reject the point at infinity.

**Ground truth:** RFC 8446 §4.2.8.2: *"For these groups, the contents of the public_key field MUST be the uncompressed point format... Peers MUST validate each other's public key Y by ensuring that the point is a valid point on the elliptic curve."*

## P1 — RFC MUSTs

### 3. HelloRetryRequest validation still missing three checks
**Issue:** `TlsHandshake.kt:195-263` rejects empty cookies and no-change HRR, but does not:
1. Reject HRR whose `supported_versions` extension is not 0x0304.
2. Reject HRR whose `cipher_suite` is not in `SupportedSuites`.
3. Save HRR's `cipher_suite` and verify the second ServerHello's `cipher_suite` matches it.

**Fix:** Parse the `supported_versions` extension from the HRR `extensions` list and require it equal 0x0304. Validate HRR's `cipher_suite` against `SupportedSuites` at parse time. Stash the HRR `cipher_suite` on the `TlsHandshake` instance and compare to the post-HRR `ServerHello.cipherSuiteCode` in `receiveAndParseServerHello`.

**Ground truth:** RFC 8446 §4.1.4: *"Upon receipt of a HelloRetryRequest, the client MUST check the legacy_version, legacy_session_id_echo, cipher_suite, and legacy_compression_method... The value of selected_group [in HRR] MUST NOT correspond to a group which was provided in the 'key_share' extension in the original ClientHello."* rustls: `client/hs.rs:299-311` — `IllegalHelloRetryRequestWithUnsupportedVersion`, `IllegalHelloRetryRequestWithUnofferedCipherSuite`.

### 4. TLS 1.3 KeyUpdate not implemented
**Issue:** `TlsHandshake.kt:320` throws `TlsException("TLS 1.3 KeyUpdate not supported")`. Any server that sends KeyUpdate kills the connection.

**Fix:** On receiving a KeyUpdate:
1. Advance the receive traffic secret with `HKDF-Expand-Label(server_app_traffic_secret_N, "traffic upd", "", Hash.length)`, then re-derive the receive key+iv and rebuild `tls13Cipher` for the receive direction; reset the receive seq to 0.
2. If `request_update == update_requested(1)`, send a `KeyUpdate(update_not_requested)` and apply the same rotation to the send direction.

**Ground truth:** RFC 8446 §4.6.3 / §7.2: *"application_traffic_secret_N+1 = HKDF-Expand-Label(application_traffic_secret_N, 'traffic upd', '', Hash.length)... If the request_update field is set to 'update_requested', then the receiver MUST send a KeyUpdate of its own with request_update set to 'update_not_requested' prior to sending its next Application Data record."* rustls: `client/tls13.rs:1500-1527`.

### 5. TLS 1.3 Certificate `certificate_request_context` not echoed
**Issue:** `TlsHandshake.kt:520-522` sends a hardcoded `byteArrayOf(0, 0, 0, 0)` (context_len=0). The CertificateRequest's actual context is never parsed.

**Fix:** Parse the CertificateRequest message — first byte is `certificate_request_context_length`, followed by that many bytes of context. Store it; echo it byte-for-byte in the client Certificate response.

**Ground truth:** RFC 8446 §4.4.2: *"certificate_request_context: If this message is in response to a CertificateRequest, the value of certificate_request_context in that message. Otherwise (in the case of server authentication), this field SHALL be zero length."*

### 6. ServerHello `legacy_version` not pinned to 0x0303
**Issue:** `TlsRecordIO.kt:136`: `TlsVersion.byCode(r.readShort())` accepts both 0x0303 and 0x0304.

**Fix:** In `parseServerHello`, require `legacy_version == 0x0303`; raise `illegal_parameter` otherwise.

**Ground truth:** RFC 8446 §4.1.3: *"In TLS 1.3, the TLS server indicates its version using the 'supported_versions' extension (Section 4.2.1), and the legacy_version field MUST be set to 0x0303, which is the version number for TLS 1.2."* rustls: `client/hs.rs:189-194`.

### 7. Advertised but unimplemented `rsa_pkcs1_sha1`
**Issue:** `TlsHandshakeIO.kt:101` advertises `(2,1)` (rsa_pkcs1_sha1), but `digestAlgorithmForHash` (`TlsDigest.kt:51-55`) only knows hashes 4/5. A server that picks SHA-1 hits an unhandled-hash error mid-handshake.

**Fix:** Remove `byteArrayOf(2, 1)` from the advertised list. SHA-1 in signatures is deprecated and the implementation does not need it.

**Ground truth:** RFC 8446 §4.2.3: *"The following values are reserved by this document and MUST NOT be used in TLS 1.3: rsa_pkcs1_sha1, ecdsa_sha1."*

### 8. `MAX_TLS_FRAME_SIZE` exceeds TLS 1.3 ceiling
**Issue:** `TlsRecordIO.kt:12` sets `MAX_TLS_FRAME_SIZE = 0x4800` (18432), the TLS 1.2 cap. TLS 1.3 caps `TLSCiphertext.length` at 16640 (2^14 + 256).

**Fix:** Parameterize the cap by negotiated version. Once `isTls13`, enforce `length <= 16640`; before version negotiation, the TLS 1.2 cap is fine.

**Ground truth:** RFC 8446 §5.2: *"The length (in bytes) of the following TLSCiphertext.encrypted_record... MUST NOT exceed 2^14 + 256 bytes."*

## P2 — Conformance / hygiene

### 9. TLS 1.3 EncryptedExtensions contents not validated
**Issue:** `TlsHandshake.kt:470` adds EE to the transcript but does not parse it. Extensions disallowed in TLS 1.3 (e.g. `ec_point_formats`, `extended_master_secret`, `session_ticket`) would be silently accepted.

**Fix:** Iterate EE's extension list; reject any type in the TLS 1.3 disallow-list.

**Ground truth:** RFC 8446 §4.2 Table 7: only `server_name`, `max_fragment_length`, `supported_groups`, `use_srtp`, `heartbeat`, `application_layer_protocol_negotiation`, `client_certificate_type`, `server_certificate_type`, `early_data` are permitted in EE. rustls: `DISALLOWED_TLS13_EXTS` in `client/tls13.rs:1684`.

### 10. TLS 1.3 message order not enforced
**Issue:** `TlsHandshake.kt:466-510` dispatches on type without enforcing the order `EncryptedExtensions, [CertificateRequest], Certificate, CertificateVerify, Finished`. Out-of-order Certificate is caught only because `serverPublicKey` is null at CertificateVerify time.

**Fix:** Track a small state enum (`AwaitEE → AwaitCertOrCertReq → AwaitCert → AwaitCV → AwaitFin`) and reject unexpected types.

**Ground truth:** RFC 8446 §2 Figure 1 and §4.

### 11. Apple custom-anchor path does not support IPv6 SANs
**Issue:** `CertInfo.kt:206-209` throws `"IPv6 SAN matching not yet supported"`. Linux's `X509_check_ip_asc` handles IPv6.

**Fix:** Parse IPv6 literals (`::` shorthand, embedded IPv4) into a 16-byte representation; compare byte-for-byte against `SubjectAltName.Ip` entries of length 16.

**Ground truth:** RFC 5280 §4.2.1.6: *"For IP version 6, as specified in [RFC2460], the octet string MUST contain exactly sixteen (16) octets."* RFC 6125 §1.7.2: an iPAddress SAN is the only valid match for an IP literal identifier.

### 12. Finished verify_data comparison is not constant-time
**Issue:** `TlsHandshake.kt:636` (TLS 1.3) and `:747` (TLS 1.2) use `ByteArray.contentEquals`, which short-circuits.

**Fix:** Replace with a XOR-accumulating comparator that scans both arrays in full and returns whether the accumulator is zero.

**Ground truth:** rustls uses `subtle::ConstantTimeEq`. Risk is theoretical for Finished (server doesn't choose the value), but the practice is universal in TLS stacks.

### 13. P-521 declared but not advertised in supported_groups
**Issue:** `TlsRecordIO.kt:192` declares `CurveInfo.Secp521r1`; `TlsHandshake.kt:435, 592, 696, 834` supports it in CertificateVerify / TLS 1.2 ECDHE; `TlsHandshakeIO.kt:92` advertises `ecdsa_secp521r1_sha512` in signature_algorithms. But `buildECCurvesExtension` only advertises groups `23, 24` — group 25 (P-521) is never reachable for key exchange.

**Fix:** Either add `25` to the supported_groups extension and the HRR group switch, or remove P-521 from `CurveInfo` and the CertificateVerify case. Pick one and make the code self-consistent.

**Ground truth:** RFC 8446 §4.2.7 — `supported_groups` advertises what the client can negotiate. Don't advertise unused entries; don't carry unreachable code.

### 14. `closeGracefully` does not wait for peer close_notify
**Issue:** `TlsHandshake.kt:390` sends close_notify and tears down without observing the peer's response.

**Fix (optional):** Document the half-close behavior in the KDoc, or add a configurable short wait for the peer's close_notify before socket close.

**Ground truth:** RFC 8446 §6.1: *"Each party MUST send a 'close_notify' alert before closing its write side of the connection... It is not required for the initiator of the close to wait for the responding close_notify alert before closing the read side of the connection."*

## P3 — Interop (not correctness)

### 15. No X25519
Modern TLS 1.3 servers prefer X25519 (`group 0x001D`); without it every connection to such servers costs one HRR round-trip. Add to `CurveInfo` and `buildECCurvesExtension`. RFC 8446 §B.3.1.4 / RFC 8422 §5.1.1.

### 16. No Ed25519 / Ed448 signature schemes
Adds support for certs signed with `ecdsa_ed25519` (`0x0807`) — increasingly common. RFC 8446 §4.2.3.

### 17. `TLS_RSA_WITH_AES_128_GCM_SHA256` (static-RSA) is offered
Non-PFS; most servers reject. Consider removing from `Tls12Suites`. RFC 8446 §1.2 deprecates it in TLS 1.3; in TLS 1.2 it remains valid but is widely disabled.

### 18. No ALPN
Not needed for NATS, but flag for protocols that need it. RFC 7301.

## Risk ranking

| # | Issue | Severity |
|---|---|---|
| 1 | Apple custom-anchor skips RFC 5280 constraints | **High** |
| 2 | EC point validation delegated to library | **High** (if library doesn't validate) |
| 3 | HRR validation missing supported_versions / cipher_suite checks | Medium |
| 4 | KeyUpdate not implemented | Medium (interop break with long-lived conns) |
| 5 | Certificate context not echoed | Low |
| 6 | `legacy_version` not pinned | Low |
| 7 | Advertised-but-unsupported SHA-1 | Low |
| 8 | Frame size cap too generous in TLS 1.3 | Low |
| 9–14 | Conformance / hygiene | Low |
| 15–18 | Interop | Low |

Running the IETF BoGo test suite against this implementation would catch 3–8 mechanically and is the recommended next step.

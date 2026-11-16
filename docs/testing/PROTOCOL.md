# TVText wire protocol

TVText clients and the TV receiver communicate over a pinned TLS 1.2 TCP connection on port `45831`. Discovery uses mDNS separately; credentials are sent only after the client validates the TV certificate fingerprint.

## Frame format

Each message is a 4-byte unsigned, big-endian body length followed by the body. Protocol version 1 uses UTF-8 strings prefixed by a 4-byte big-endian byte length.

Request body:

| Field | Encoding |
|---|---|
| Version | 1 byte, `1` |
| Command | 1 byte: pair `1`, status `2`, insert `3`, revoke `4` |
| Credential | UTF-8 string, at most 256 bytes |
| Device/request ID | UTF-8 string, at most 128 bytes |
| Text | UTF-8 string, at most 32,768 bytes |

Response body:

| Field | Encoding |
|---|---|
| Version | 1 byte, `1` |
| Result | 1 byte: `0` success, `1` authorization, `2` pairing closed, `3` bad code, `4` no input field, `5` receiver disabled, `6` malformed request, `9` uncertain delivery |
| Ready | 1 byte boolean (`0` or `1`) |
| Message | UTF-8 string, at most 4,096 bytes |
| Token | UTF-8 string, at most 256 bytes |
| Target | UTF-8 string, at most 1,024 bytes |
| TV device ID | UTF-8 string, at most 128 bytes |

The maximum frame body is 65,536 bytes. Receivers reject unsupported versions, invalid UTF-8, invalid lengths, invalid boolean/result values, and trailing bytes. Insert requests carry an idempotency ID so a repeated request does not insert the same text twice.

## Shared compatibility vector

`tests/shared/protocol/v1-vectors.txt` is consumed by the Android TV, Android mobile, and Windows client tests. It contains a compact request with Cyrillic, emoji, and a newline plus a response. Each implementation must match the exact encoded bytes, then parse the same response bytes. Update all implementations and the vector together when changing the wire format.

## Local transport diagnostics

Diagnostics describe only the failed stage (TCP connection, TLS/certificate validation, or protocol response). They must not include message text, pairing codes, credentials, tokens, certificate fingerprints, or discovered network addresses.

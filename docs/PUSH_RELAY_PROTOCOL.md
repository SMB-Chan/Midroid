# Midroid push relay protocol — v1 client view

This document specifies the version 1 relay contract from the Android client's perspective.
It is the implementable form of the security requirements in `NOTIFICATIONS.md`
(Relay security requirements) and does not replace that rationale document.
There is no relay server implementation in this repository; any server claiming
compatibility must satisfy this contract.

Status: **client foundation only**. The app implements key generation, subscription
records, endpoint validation, envelope validation and on-device decryption as pure
JVM units. Delivery transport (FCM data / UnifiedPush / self-hosted), relay hosting,
and notification display are separate follow-up steps.

## Roles

- **Midroid app (user agent):** owns the P-256 receiver private key and the 16-octet
  Web Push authentication secret. Registers the relay endpoint plus only the receiver
  **public** key and auth secret with Misskey through the normal `sw/register` contract.
- **Relay:** exposes an unguessable HTTPS endpoint per subscription, validates inbound
  sender POSTs, and forwards the encrypted envelope to exactly one Android delivery
  transport without decrypting it in normal operation.
- **Misskey instance (application server):** ordinary Web Push sender. Uses its normal
  sender path (VAPID + RFC 8291 `aes128gcm`) against the relay endpoint. No
  Midroid-specific server extension is required or allowed.

## Endpoint format

- Relay subscription endpoints are HTTPS URLs of the form:

  ```text
  https://<relay-host>/v1/p/<endpoint-id>
  ```

- `<endpoint-id>` is base64url (no padding), decoding to **at least 128 bits** of
  server-generated entropy. IDs must be independently revocable per subscription.
- The full endpoint URL is a capability URL: possession allows sending encrypted
  pushes to one subscription. It is secret-equivalent and must never be logged.
- Client validation (`RelayProtocol.parseEndpointId`):
  - scheme must be `https`;
  - a network host must be present;
  - embedded URL credentials are rejected;
  - path must match `/v1/p/<id>` with sufficient decoded entropy.

## Subscription lifecycle (illustrative HTTP, protocol v1)

Relay hosting and authentication are out of scope for the client foundation, but the
message shapes below are fixed so hosted and self-hosted relays share one protocol:

1. App generates a fresh P-256 receiver key pair and a 16-octet auth secret on-device.
2. App creates a relay subscription, binding it to an Android delivery target.
   Transport identifiers (FCM registration token, UnifiedPush endpoint, …) belong at
   this relay boundary and **must not** be sent to Misskey.
3. Relay returns the canonical `https://<relay-host>/v1/p/<endpoint-id>` URL.
4. App registers `{endpoint, publickey, auth}` with Misskey via `sw/register`
   (see `MisskeyPushRegistration`; exact field casing/encoding is gated by the
   live-verification checklist in `NOTIFICATIONS.md` before any network call ships).
5. Rotation replaces the endpoint atomically: create new → register new →
   unregister old. Deletion revokes the endpoint; the relay must answer revoked IDs
   with 404/410 and must not redeliver.

Relay credentials must never be usable as Web Push receiver private keys
(key separation).

## Sender-to-relay POST (Misskey → relay)

The relay must accept the sender's ordinary RFC 8291 POST and forward it verbatim:

```text
POST /v1/p/<endpoint-id>
Content-Encoding: aes128gcm
Encryption: salt=<base64url-16B>
Crypto-Key: dh=<base64url-senderPub65B>[; p256ecdsa=<vapidPub>]
Authorization: vapid t=..., k=...
TTL: <seconds>
Urgency: <very-low|low|normal|high>
Content-Length: <header + ciphertext + tag>

<body: salt(16) || rs(4BE) || idlen(1) || keyid(senderPub) || ciphertext+16B-tag>
```

Relay obligations on receipt:

- look up `<endpoint-id>`; unknown/revoked → 404/410, no delivery attempt;
- enforce `Content-Length ≤ 8192` (client constant `MAX_ENVELOPE_BYTES`; tunable after
  live capture — RFC 8030 permits push services to cap bodies at 4096 octets, so the
  relay cap must cover header + body of a compliant message);
- enforce request/header size limits and rate limits per subscription and per sender;
- apply replay/duplicate suppression over a bounded window;
- pass `TTL`/`Urgency` through where the downstream transport can preserve them;
- forward headers + body bytes **verbatim** to the bound transport;
- never decrypt the payload in normal operation; never log request/header/body bytes;
- retain only minimal delivery metadata (subscription id, timestamps, outcome).

If a downstream transport cannot carry the envelope intact (notably FCM data-message
size limits versus a ~4 KiB record plus headers), that transport is unsuitable unless
a comparably end-to-end encrypted encapsulation is added. Falling back to relay-side
plaintext must never be silent.

## Relay-to-device envelope (transport-opaque)

Whatever transport carries the delivery, the app receives exactly:

- `endpointId` (which relay subscription this is for);
- the sender's `Content-Encoding` / `Encryption` / `Crypto-Key` headers verbatim;
- the raw body bytes verbatim;
- optional `TTL` / `Urgency` passthrough values.

The app looks up the subscription by `endpointId`, runs `PushEnvelope.validate()`,
then decrypts with `WebPushDecryptor` using only on-device key material. Only after
authenticated decryption succeeds may the plaintext be parsed or displayed.

## What the relay still learns (non-goals of opacity)

Opaque forwarding reduces trust in the relay but does not make it metadata-blind.
The relay observes subscription identity, timing, sender network information and
ciphertext length. Clients must treat the endpoint URL as sensitive and rotate it
on account logout, device change, or suspected exposure.

## Versioning

Breaking changes require a new path version (`/v2/…`). Additive header passthrough
is allowed. The client records the protocol version it validated against in
`RelayProtocol.PROTOCOL_VERSION`.

# Native notification architecture

Midroid's WebView MVP intentionally does not fake browser Web Push support. Notifications need a separate architecture because Android System WebView does not expose the browser `PushManager` environment that Misskey's service worker expects.

## What Misskey expects

Current Misskey exposes the authenticated `sw/register` endpoint in its generated API surface. A Web Push subscription supplies an endpoint plus the receiver's authentication secret and P-256 public key (`p256dh` / Misskey's `publickey` field), together with optional read-message behavior.

The important compatibility boundary is the Web Push subscription contract, not a Midroid-specific server extension. If Midroid can provide a standards-compatible subscription endpoint and receiver key material, an existing Misskey instance can continue using its normal push sender path.

## Security property to preserve

Web Push message encryption is designed to keep payload contents confidential from the push service itself. The receiver generates a P-256 ECDH key pair and an authentication secret for a subscription; the application server encrypts to the receiver public key, while decryption requires the receiver private key.

Midroid should preserve that end-to-end property:

- the Android app owns and stores the subscription private key;
- the relay never receives that private key;
- the relay forwards an opaque encrypted Web Push envelope;
- decryption occurs on-device immediately before notification processing.

A relay will still observe metadata such as subscription identity, timing, sender network information and ciphertext length. Opaque forwarding reduces trust in the relay; it does not make the relay metadata-blind.

## Preferred architecture: opaque Web Push relay -> native delivery

A separately deployable relay exposes an unguessable HTTPS Web Push endpoint for each Midroid subscription. The high-level flow is:

1. Midroid generates a fresh P-256 receiver key pair and a cryptographically random Web Push authentication secret.
2. Midroid creates a relay subscription and associates it with an Android delivery target.
3. Midroid registers the relay endpoint plus only the receiver **public** key and auth secret with Misskey through the normal `sw/register` contract.
4. Misskey sends its ordinary encrypted Web Push POST to the relay endpoint.
5. The relay validates the subscription, applies size/rate/replay controls, and forwards the encrypted envelope through an Android delivery transport.
6. Midroid receives the envelope and decrypts it locally using the subscription private key and auth secret.
7. Only after successful authenticated decryption does Midroid parse and display a notification.

The relay must not terminate Web Push payload confidentiality by decrypting the message as part of normal operation.

### Delivery transports

The opaque relay can have pluggable delivery backends:

- FCM data delivery for devices with Google Play services;
- UnifiedPush for users who prefer a compatible distributor;
- a future self-hosted transport where practical.

Transport-specific identifiers belong at the relay boundary and should not leak into Misskey's subscription API.

### Relay security requirements

Before implementation, the relay protocol needs explicit handling for:

- unguessable, independently revocable subscription endpoint IDs;
- authenticated device registration and rotation;
- replay and duplicate suppression;
- request body and header size limits;
- rate limiting and abuse controls;
- TTL / urgency semantics where the downstream transport can preserve them;
- atomic subscription replacement and deletion;
- no request/body logging by default;
- minimal retention of delivery metadata;
- key separation: relay credentials must never be usable as Web Push receiver private keys.

If a downstream transport cannot carry the encrypted envelope intact, that transport is unsuitable unless a comparably end-to-end encrypted encapsulation is added. Falling back to relay-side plaintext should not be silent.

## Self-hosted relay

The same opaque protocol should be deployable by a user or instance operator. Self-hosting reduces central trust and can enable UnifiedPush-first deployments, at the cost of setup and maintenance complexity.

The hosted and self-hosted variants should share the same protocol so Midroid does not need separate notification implementations.

## Background Misskey WebSocket

A native service could authenticate to Misskey and keep a streaming connection alive.

This is technically simpler than a push relay but conflicts with Midroid's battery objective, creates Android background-execution complexity, and requires reliable native credential provisioning. It should be treated as a diagnostic/control implementation, not the default notification design.

## Periodic notification sync

A native scheduled worker could periodically query notifications.

This requires native API credentials and is not realtime, but it is a useful low-complexity fallback for users who prefer no relay. Android scheduling limits mean it should not be presented as equivalent to push.

## Current decision

For the 0.1 WebView MVP:

1. do not keep a native background WebSocket solely for notifications;
2. do not inject a broad JavaScript interface into arbitrary Misskey pages;
3. preserve the existing Misskey `sw/register` / Web Push contract as the compatibility target;
4. keep Web Push receiver private keys on-device;
5. require any future relay to forward encrypted envelopes rather than normally decrypting them;
6. benchmark foreground/background power first;
7. prototype the relay as a separate component only after the WebView MVP is stable on real devices.

The notification layer must never require weakening WebView origin isolation or SSL validation.

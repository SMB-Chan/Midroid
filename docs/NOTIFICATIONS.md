# Native notification architecture

Midroid's WebView MVP intentionally does not fake browser Web Push support. Notifications need a separate architecture because Android System WebView does not expose the browser `PushManager` environment that Misskey's service worker expects.

## What Misskey expects

Current Misskey registers push subscriptions through its authenticated `sw/register` API. A registration contains:

- `endpoint`
- `auth`
- `publickey` (`p256dh`)
- optional read-message behavior

The server stores that subscription and later uses the standard `web-push` library with the instance VAPID key pair to send an encrypted payload to the registered endpoint.

This is useful for Midroid: a Misskey server does not need to understand Midroid specifically. If Midroid can provide a standards-compatible Web Push subscription endpoint and keys, the existing Misskey push path can remain unchanged.

## Candidate approaches

### A. Web Push gateway -> native push

A small gateway exposes a standards-compatible Web Push endpoint, decrypts messages for a Midroid subscription, and forwards them to an Android delivery mechanism such as FCM or a UnifiedPush distributor.

Advantages:

- keeps existing Misskey instances unchanged;
- realtime delivery without a permanent WebSocket in Midroid;
- WebView can remain deeply suspended in the background.

Costs / risks:

- requires a reachable gateway service;
- the gateway becomes security/privacy-sensitive infrastructure;
- subscription-key lifecycle, replay protection, authentication and deletion must be designed carefully;
- FCM would add a Google-service dependency unless another distributor is supported.

A relay should therefore be optional and separately deployable, not silently bundled into the first WebView MVP.

### B. Self-hosted Web Push gateway

Same protocol shape as A, but the user or instance operator supplies the gateway. This minimizes central trust but increases setup complexity.

### C. Background Misskey WebSocket

A native service could authenticate to Misskey and keep a streaming connection alive.

This is technically simpler than a push gateway but conflicts with Midroid's battery objective, creates Android background-execution complexity, and would require reliable native credential provisioning. It should be treated as a diagnostic/control implementation, not the default notification design.

### D. Periodic notification sync

A native scheduled worker could periodically query notifications.

This requires native API credentials and is not realtime, but it is a useful low-complexity fallback for users who prefer no relay. Android scheduling limits mean it should not be presented as equivalent to push.

## Current decision

For the 0.1 WebView MVP:

1. do not keep a native background WebSocket solely for notifications;
2. do not inject a broad JavaScript interface into arbitrary Misskey pages;
3. preserve the existing Misskey web-push registration contract as the compatibility target;
4. benchmark foreground/background power first;
5. prototype the gateway path as a separate component only after the WebView MVP is stable on real devices.

The notification layer must never require weakening WebView origin isolation or SSL validation.

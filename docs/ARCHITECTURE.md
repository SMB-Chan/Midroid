# Midroid architecture

## MVP boundary

Midroid is not a Chromium fork in the first stage. It is a small Android runtime that delegates standards-compatible rendering to Android System WebView while taking explicit ownership of the app lifecycle and Misskey navigation boundary.

### Components

- `MainActivity`: a minimal AndroidX `ComponentActivity` that owns predictive-back dispatch, browser lifecycle and renderer recovery.
- `InstanceConfig`: validates and normalizes the single trusted Misskey origin; construction is forced through `parse()`.
- `WebViewFactory`: applies the security baseline for WebView, standards-respecting cache policy and debug-only WebView inspection.
- `MidroidWebViewClient`: keeps the configured Misskey origin in-app and routes other top-level links externally.
- `WebViewPowerController`: maps Eco/Balanced/Performance modes to renderer priority, foreground policy, background suspension and document-level motion policy.
- `AppPreferences`: stores only the chosen origin, power mode and text scale. Authentication cookies remain managed by WebView/Android.
- AndroidX Activity provides modern activity/back-navigation plumbing; AndroidX WebKit exposes capability-gated WebView cache/BFCache/profile APIs.

## Security model

The configured Misskey origin is the only HTTPS origin allowed to perform top-level navigation inside Midroid. Other top-level links are handed to Android. Cleartext HTTP instance configuration is rejected and cleartext traffic is disabled in the manifest/network policy. SSL errors are not bypassed. File-scheme access is disabled. Web permission requests are denied in the MVP.

The top-level-only navigation boundary is deliberate. `shouldOverrideUrlLoading` does not reinterpret subframe navigations because blocking every non-instance subresource/frame would also block legitimate federated media and embeds. A third-party frame remains a third-party Web origin; it is not promoted to Midroid's trusted instance origin, third-party cookies stay disabled, mixed cleartext content stays blocked, and only a top-level transition can be handed to an external app. If Misskey later introduces privileged cross-origin iframe flows, they must be reviewed explicitly rather than silently whitelisted.

External `mailto:`, `tel:`, `geo:` and `market:` intents are declared in manifest package-visibility queries. Runtime dispatch does not depend on `resolveActivity()` preflight; Midroid attempts the allowed intent and handles `ActivityNotFoundException`, avoiding Android 11+ package-visibility false negatives.

Release/update artifacts are non-debuggable. Web contents debugging is enabled only when Android marks the installed application as debuggable, so `chrome://inspect` remains available for developer builds without exposing the stable update track.

## Cache policy

HTTP cache quota growth is adaptive rather than unconditional. Midroid does not increase the quota when the app filesystem has less than 512 MiB free. With more headroom it uses 64 MiB, 128 MiB or 256 MiB target floors as free space crosses 512 MiB, 1 GiB and 2 GiB. Existing larger quotas and larger WebView defaults are not forcibly shrunk.

## Renderer recovery

Eco and Balanced allow an invisible renderer to be treated as lower priority by Android. Because the OS may reclaim that renderer under memory pressure, `onRenderProcessGone` is handled as a normal recovery path: the dead WebView is destroyed, a fresh one is created, and the last committed URL is restored.

## Deliberate limitations

- Midroid cannot directly close Misskey's internal WebSocket objects because those references are owned by application JavaScript. Background timer suspension helps, but a later Misskey-aware bridge or network integration is needed to guarantee socket teardown.
- Web Push is not implemented in the MVP. Native notifications are planned as a later stage.
- WebRTC camera/microphone permissions are denied until a least-privilege permission design is added.
- Only HTTPS instances are accepted in the MVP.
- JVM tests cover pure policy/state logic. Android framework behavior (SDK-specific WebView calls and renderer recovery) still needs a dedicated Robolectric/instrumented-device test layer; this remains a measurement/test milestone rather than being simulated with brittle mocks in the MVP.

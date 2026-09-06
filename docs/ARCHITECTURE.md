# Midroid architecture

## MVP boundary

Midroid is not a Chromium fork in the first stage. It is a small Android runtime that delegates standards-compatible rendering to Android System WebView while taking explicit ownership of the app lifecycle and Misskey navigation boundary.

### Components

- `MainActivity`: owns the browser lifecycle and renderer recovery.
- `InstanceConfig`: validates and normalizes the single trusted Misskey origin.
- `WebViewFactory`: applies the security baseline for WebView.
- `MidroidWebViewClient`: keeps the configured Misskey origin in-app and routes other top-level links externally.
- `WebViewPowerController`: maps Eco/Balanced/Performance modes to renderer priority, foreground policy, background suspension and document-level motion policy.
- `AppPreferences`: stores only the chosen origin and power mode. Authentication cookies remain managed by WebView/Android.

## Security model

The configured Misskey origin is the only HTTPS origin allowed to perform top-level navigation inside Midroid. Other top-level links are handed to Android. Cleartext HTTP instance configuration is rejected and cleartext traffic is disabled in the manifest/network policy. SSL errors are not bypassed. File-scheme access is disabled. Web permission requests are denied in the MVP.

## Renderer recovery

Eco and Balanced allow an invisible renderer to be treated as lower priority by Android. Because the OS may reclaim that renderer under memory pressure, `onRenderProcessGone` is handled as a normal recovery path: the dead WebView is destroyed, a fresh one is created, and the last URL is restored.

## Deliberate limitations

- Midroid cannot directly close Misskey's internal WebSocket objects because those references are owned by application JavaScript. Background timer suspension helps, but a later Misskey-aware bridge or network integration is needed to guarantee socket teardown.
- Web Push is not implemented in the MVP. Native notifications are planned as a later stage.
- WebRTC camera/microphone permissions are denied until a least-privilege permission design is added.
- Only HTTPS instances are accepted in the MVP.

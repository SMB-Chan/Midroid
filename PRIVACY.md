# Privacy

Midroid is designed as a local Android client for a Misskey instance selected by the user.

## Data handled by Midroid

Midroid stores the minimum local state needed to operate the client, including configured instance origins, app preferences, account/profile metadata used by Midroid, WebView cookies and site storage, and the last page used for restoration.

This information remains in the app/WebView storage managed by Android unless the user clears app data, removes the app, or the relevant web content clears its own storage.

## Network traffic

Web content is loaded directly by Android System WebView from the Misskey instance and from resources that page itself requests. Downloads may be handed to Android DownloadManager. Midroid does not copy WebView cookies or full-page Referer headers into DownloadManager or the native Media3 audio fallback, because those platform clients can follow redirects to another HTTPS host. A resource that requires WebView session cookies may therefore fail in those native fallback paths and should remain in the WebView until a hop-aware authenticated HTTP implementation exists.

Midroid does not operate a Midroid analytics, advertising, tracking or telemetry backend. The project does not include an analytics SDK in the application build.

The configured Misskey instance, linked third-party sites, Android System WebView provider and Android platform services may process data under their own policies. Midroid does not control those services.

## Diagnostics

Midroid can produce local diagnostics for troubleshooting. The diagnostic design intentionally avoids URL paths, query strings, fragments, note IDs, cookies and authentication tokens. Users should still review any copied diagnostic text before sharing it publicly.

## Permissions

The app directly requests Internet access. The merged APK may also contain AndroidX/Media3 library permissions such as network-state access, wake-lock support, and AndroidX's non-exported dynamic-receiver permission. Midroid does not implement a persistent background service or intentionally hold a wake lock as part of its WebView lifecycle policy. Camera and microphone WebRTC permissions are not currently granted by the Midroid client.

## Changes

Privacy-relevant behavior changes should be documented in release notes and, when appropriate, in this file before a public release.

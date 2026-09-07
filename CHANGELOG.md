# Changelog

All notable user-facing changes to Midroid will be documented here.

The project follows semantic versioning for public release tags (`vMAJOR.MINOR.PATCH`).

## [Unreleased]

### Fixed

- Preserve the intrinsic aspect ratio of wide timeline reaction emoji so long horizontal artwork scales by height instead of being squeezed into a square and appearing tiny.
- Increase the visible timeline reaction emoji height for Standard / Large / Extra Large modes while keeping a capped width for exceptionally wide custom emoji.
- Enlarge notification status sub-icons such as login, access-token, reply and renote badges independently from reaction artwork.
- Enlarge grouped-notification header symbols such as `+`, heart and renote, which Misskey otherwise keeps at a fixed 15px glyph size.
- Render single notification reactions below the avatar and grouped reaction items as avatar/reaction stacks, preserving reaction aspect ratio and avoiding avatar overlap.
- Clarify reaction-size settings by distinguishing tap-target size from actual emoji height.

## [0.1.2] - 2026-09-08

Patch release improving notification readability and reaction layout.

### Fixed

- Enlarge Misskey notification text, avatars, and grouped-reaction items according to Midroid's reaction-size setting.
- Prevent reaction badges from covering user avatars by moving reaction notifications into dedicated avatar/reaction lanes instead of enlarging Misskey's original absolute overlay.
- Increase notification reaction sizes to 32px / 40px / 48px for Standard / Large / Extra Large while keeping avatars fully visible.
- Restrict notification reaction CSS to the MkNotification structure instead of broadly resizing matching emoji containers elsewhere on the page.

## [0.1.1] - 2026-09-07

Patch release fixing the native audio fallback UI scope.

### Fixed

- Restrict the floating native-audio fallback to the currently active audio item inside Misskey's `#pswp` lightbox, preventing `Androidで再生` / `Play with Android` from appearing on ordinary timeline notes, image/video viewers, or stale hidden audio elements.

## [0.1.0] - 2026-09-07

First public preview release.

### Added

- Kotlin Android client built around Android System WebView.
- Arbitrary HTTPS Misskey instance configuration.
- Same-origin in-app navigation and external-link routing.
- Eco, Balanced and Performance power modes.
- Lifecycle-aware WebView suspension and renderer recovery.
- Density-aware text scaling and configurable reaction scaling.
- Multi-account/profile support where the installed WebView provider supports WebView multi-profile APIs.
- File picker and DownloadManager handoff with WebView credentials intentionally excluded from redirecting native clients.
- Native audio fallback for supported Misskey media.
- Privacy-safe runtime diagnostics and reproducible ADB benchmark tooling.
- Dedicated persistent release-signing path for update-compatible APKs.
- Public GitHub release pipeline for signed APKs, SHA-256 checksums and signing-certificate reports.
- Public security, privacy, contribution and release documentation.

### Security

- Redirecting native download/audio clients no longer receive WebView Cookie or full-page Referer headers.
- Delayed native-audio readiness cannot auto-start after the app becomes hidden, and hidden progress polling is stopped.
- Eco autoplay suppression now observes media added later by Misskey SPA updates.
- Unsupported saved WebView profiles recover to the default account or a visible recovery UI instead of leaving a blank startup state.
- Public release `versionCode` generation is monotonic across the former run-number `% 100` boundary and is checked against the previous release metadata.
- Cleartext traffic is disabled.
- TLS/SSL errors are not bypassed.
- WebView remote inspection is restricted to debuggable builds.
- Release signing material is excluded from version control and supplied to CI only through repository secrets.

### Known limitations

- This is an experimental preview, not a hardened replacement for a general-purpose browser.
- Native Web Push integration is not yet implemented.
- WebRTC camera/microphone permission is not currently granted by the client.
- Behavior can vary with Android System WebView version and Misskey instance version/configuration.

# Changelog

All notable user-facing changes to Midroid will be documented here.

The project follows semantic versioning for public release tags (`vMAJOR.MINOR.PATCH`).

## [Unreleased]

### Added

- Public GitHub release pipeline for signed APKs, SHA-256 checksums and signing-certificate reports.
- Public security, privacy, contribution and release documentation.

## [0.1.0] - Unreleased

First public preview target.

### Added

- Kotlin Android client built around Android System WebView.
- Arbitrary HTTPS Misskey instance configuration.
- Same-origin in-app navigation and external-link routing.
- Eco, Balanced and Performance power modes.
- Lifecycle-aware WebView suspension and renderer recovery.
- Density-aware text scaling and configurable reaction scaling.
- Multi-account/profile support where the installed WebView provider supports WebView multi-profile APIs.
- File picker and authenticated DownloadManager handoff.
- Native audio fallback for supported Misskey media.
- Privacy-safe runtime diagnostics and reproducible ADB benchmark tooling.
- Dedicated persistent release-signing path for update-compatible APKs.

### Security

- Cleartext traffic is disabled.
- TLS/SSL errors are not bypassed.
- WebView remote inspection is restricted to debuggable builds.
- Release signing material is excluded from version control and supplied to CI only through repository secrets.

### Known limitations

- This is an experimental preview, not a hardened replacement for a general-purpose browser.
- Native Web Push integration is not yet implemented.
- WebRTC camera/microphone permission is not currently granted by the client.
- Behavior can vary with Android System WebView version and Misskey instance version/configuration.

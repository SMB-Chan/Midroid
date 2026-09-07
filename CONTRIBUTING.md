# Contributing to Midroid

Thank you for helping improve Midroid. The project is intentionally conservative about changes that increase browser/security maintenance cost: measure first, then add the smallest native or WebView-specific control that solves a demonstrated problem.

## Development environment

Current requirements:

- JDK 17+
- Android SDK 36
- Gradle 9.6.0, or a compatible Android Studio installation

Run the same core checks used by CI:

```text
gradle lint testDebugUnitTest assembleDebug assembleRelease
```

The release build can be compiled without the private update signing key, but only a build signed with the project's persistent release identity is suitable for the public update channel.

## Pull requests

Keep each pull request focused and describe:

1. the problem being solved;
2. the Android/WebView/Misskey conditions used to reproduce it;
3. the implementation boundary;
4. validation performed;
5. any measurable power, performance, privacy or compatibility effect.

For performance or battery claims, prefer reproducible A/B measurements over subjective impressions. See `docs/BENCHMARKING.md`.

## Security and privacy

Never commit:

- signing keystores or private keys;
- passwords, API tokens, cookies or session data;
- real account exports;
- captures containing private Misskey content unless fully sanitized.

Potential vulnerabilities should follow `SECURITY.md`, not a public issue.

## Compatibility

Midroid targets arbitrary HTTPS Misskey instances rather than one hard-coded server. Avoid assumptions that depend on one instance's hostname or private configuration.

Changes to navigation, cookies, downloads, WebView profiles, TLS handling or JavaScript injection should be treated as security-sensitive and reviewed accordingly.

## Licensing

By contributing, you agree that your contribution may be distributed under the repository's Apache License 2.0.

# Public release audit remediation — 2026-09-07

This document records the remediation applied after the 2026-09-07 pre-publication audit. It does not replace physical-device release validation.

## Status

The five confirmed audit findings have code-level remediations:

- **F1 / P1 — redirect credential forwarding:** native Media3 audio and Android DownloadManager no longer receive WebView Cookie or full-page Referer headers. Only non-sensitive transport metadata such as User-Agent is handed to redirecting native clients. This is intentionally fail-closed: a resource that requires WebView session cookies can fail in the native fallback until Midroid owns redirect handling hop-by-hop and can remove credentials on an origin change.
- **F2 / P2 — hidden native-audio preparation/progress:** host visibility and playback intent are explicit state. `onStop` clears the auto-play intent, removes progress/prepare callbacks and prevents delayed READY from starting playback while hidden. Interrupted first preparation becomes an explicit Retry state.
- **F3 / P2 — SPA-added autoplay media:** Eco mode now observes added DOM nodes and neutralizes autoplay only for newly introduced media with autoplay intent. Leaving Eco disconnects the observer.
- **F4 / P2 — unsupported saved WebView profile:** startup first attempts a safe switch to a valid default account without opening the named profile in default storage. If that is unavailable, or WebView profile creation fails, Midroid displays a recovery screen with Retry, Accounts and Settings actions.
- **F5 / P2 — decreasing release `versionCode`:** public releases use `1000000 + GITHUB_RUN_NUMBER`; boundary tests cover the former 99→100 failure. The release job also reads the previous GitHub Release's version-code asset and fails unless the candidate is strictly greater.

## Release-pipeline hardening

The tag-driven public release workflow now:

1. accepts only version-shaped `v*.*.*` tags;
2. verifies through the GitHub API that the tagged commit is already contained in `main`, avoiding an unauthenticated `git fetch` after `persist-credentials: false`;
3. requires all four persistent signing secrets and refuses unsigned publication;
4. runs release-version boundary tests, Android lint, JVM tests and the minified release build;
5. verifies the resulting APK signing-certificate SHA-256 digest;
6. attaches the APK, APK SHA-256, signing report and numeric version-code asset to the GitHub Release;
7. refuses a later release whose `versionCode` is not greater than the previous published release.

README, PRIVACY, RELEASING and SECURITY documentation were updated to match the actual distribution and credential-handling behavior.

## Verification performed

The exact audit-remediation tree produced by commit `ca9895887e6fff1bf980e776556972bd411f8c2f` was verified in GitHub Actions with:

```text
gradle --no-daemon --stacktrace lint testDebugUnitTest assembleDebug assembleRelease
```

The build completed successfully, including R8/resource shrinking, and `tools/test_release_version_code.sh` passed. The initial automation could not push that commit because the job-scoped `GITHUB_TOKEN` lacked permission to update workflow files; the already-verified commit object was then fast-forwarded to the remediation branch using the repository-authorized GitHub connection. Japanese recovery strings were subsequently localized and must pass the normal branch/PR CI before merge.

## Remaining publication gates

These are intentionally not claimed as complete by code CI:

- run the final signed, non-debuggable APK on at least one physical Android device;
- verify launch, retained login, posting/attachment flow, account switching, a second instance, download behavior, Back, native audio, renderer recovery, rotation/IME/insets;
- where practical, cover a device near minSdk and API 35/36 differences;
- build/install two consecutive APK versions with the persistent release key and verify in-place update, retained app/WebView data, increasing `versionCode` and matching signing certificate;
- do not advertise measured lower power/RAM use than Chrome/PWA until the documented repeated A/B/A benchmark is actually recorded.

No public GitHub Release should be created before those device/update gates are complete.

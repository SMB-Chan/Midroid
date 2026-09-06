# Midroid

Midroid is an experimental Android runtime optimized for using Misskey with less unnecessary foreground rendering and background work than a generic browser/PWA configuration.

The first stage deliberately **does not fork Chromium**. It uses Android System WebView (Chromium-based) as the rendering engine and puts a small native control layer around the parts a generic browser cannot specialize for Misskey: lifecycle suspension, renderer priority, navigation boundaries and explicit power modes.

## MVP features

- Native Kotlin Android app with no UI framework; AndroidX Activity is used only for modern activity/back-navigation plumbing.
- Connect to an arbitrary HTTPS Misskey instance.
- Preserve WebView cookies/login state.
- Keep same-origin Misskey navigation inside the app.
- Open non-instance top-level links in the system browser/app.
- Eco / Balanced / Performance power modes.
- Visibility-aware deep suspension: `WebView.onPause()` + `pauseTimers()` only after the activity reaches `onStop()`.
- Renderer priority policy with `onRenderProcessGone` recovery.
- Eco reduced-motion and autoplay suppression.
- Eco 60 Hz frame-rate hint: View-level request on Android 15/API 35, propagated through the WebView hierarchy on Android 16/API 36+, with a window-level refresh-rate hint on Android 8-14.
- Density-aware WebView text scaling with Auto and fixed zoom choices.
- Larger standards-respecting HTTP cache quota on supported WebView versions, plus BFCache and service-worker cache-policy tuning.
- Interruptible media loading: Android Back cancels in-flight WebView loading before navigating away and tracks Misskey lightbox history.
- File picker support for Misskey attachments.
- Authenticated HTTPS downloads through Android DownloadManager.
- Cleartext traffic disabled; SSL errors are never bypassed.
- Privacy-safe `MidroidDiag` lifecycle/renderer logging for power experiments.
- Settings-screen diagnostic copy containing only device/WebView version, selected power mode, power-saver state and the sanitized Misskey origin.
- Reproducible ADB capture tooling for Chrome/PWA vs Midroid comparisons, including an automated background scenario.
- Optional stable CI signing track for update-compatible APKs that preserve Android app data and WebView login state across in-place updates.

## Build

Requirements:

- JDK 17+
- Android SDK 36
- Android Studio Quail 4 (2026.1.4) or a compatible newer version

A checked-in Gradle wrapper will be added after the initial toolchain bootstrap. For now, use Android Studio's configured Gradle or Gradle 9.6.0.

Run:

```text
gradle lint testDebugUnitTest assembleDebug
```

The debug APK is produced under `app/build/outputs/apk/debug/`. GitHub Actions always uploads a `Midroid-ci-debug-apk` verification artifact. When the four stable signing secrets documented in `docs/UPDATES.md` are configured, CI also uploads `Midroid-update-apk`; use that update track for installations that must keep login state between versions. Until those secrets are configured, the update-compatible artifact is intentionally skipped rather than being mislabeled as safe for in-place updates.

Every CI artifact includes an APK SHA-256 file and `apksigner --print-certs` output so the signing identity can be checked between builds. CI also assigns a monotonically increasing `versionCode` from the workflow run number.

See `docs/UPDATES.md` before installing the stable update track. Historical CI debug builds used temporary runner signing identities, so the first migration to the stable signing key can require one reinstall; later stable-track APKs update in place.

## Measure before forking Chromium

Midroid includes a raw-data ADB benchmark collector:

```text
bash tools/benchmark_android.sh dev.midroid.app 600 midroid-balanced-01
```

For a repeatable background-residence test:

```text
MIDROID_SCENARIO=background MIDROID_WARMUP_SECONDS=10 \
  bash tools/benchmark_android.sh dev.midroid.app 1200 midroid-balanced-background-01
```

Use the same collector with Chrome or the installed WebAPK package to build an A/B dataset. The collector stores battery, CPU, process, memory, frame timing, network and WebView/lifecycle evidence rather than reducing everything to a single opaque score.

`MidroidDiag` intentionally logs only the Misskey origin, power mode, WebView provider/version, Android SDK/power-saver state, lifecycle transitions and renderer termination reason. URL paths, note IDs, query strings and fragments are excluded.

See `docs/BENCHMARKING.md` for the controlled A/B/A protocol.

## Why WebView first?

Forking Chromium immediately would make Midroid responsible for fast-moving browser security updates and a large native build surface before we have measured which changes actually save power. The WebView stage is designed to answer that experimentally. If profiling shows a bottleneck that WebView APIs cannot control, that evidence defines the smallest useful Chromium patch set.

Native notifications are intentionally kept as a separate subsystem. Current Misskey already emits standard Web Push; Midroid's compatibility target is a standards-compatible subscription endpoint rather than a permanent background WebSocket. See `docs/NOTIFICATIONS.md`.

See `docs/ARCHITECTURE.md`, `docs/POWER_POLICY.md`, `docs/LIFECYCLE.md`, `docs/BENCHMARKING.md`, `docs/NOTIFICATIONS.md` and `docs/UPDATES.md` for the design boundaries and measurement plan.

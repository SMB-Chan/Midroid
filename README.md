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
- Background `WebView.onPause()` + `pauseTimers()` suspension.
- Renderer priority policy with `onRenderProcessGone` recovery.
- Eco reduced-motion and autoplay suppression.
- 60 Hz preference in Eco mode.
- File picker support for Misskey attachments.
- Authenticated HTTPS downloads through Android DownloadManager.
- Cleartext traffic disabled; SSL errors are never bypassed.

## Build

Requirements:

- JDK 17+
- Android SDK 36
- Android Studio Quail 4 (2026.1.4) or a compatible newer version

A checked-in Gradle wrapper will be added after the initial toolchain bootstrap. For now, use Android Studio's configured Gradle or Gradle 9.6.0:

Then run:

```text
gradle lint testDebugUnitTest assembleDebug
```

The debug APK is produced under `app/build/outputs/apk/debug/`.

## Why WebView first?

Forking Chromium immediately would make Midroid responsible for fast-moving browser security updates and a large native build surface before we have measured which changes actually save power. The WebView stage is designed to answer that experimentally. If profiling shows a bottleneck that WebView APIs cannot control, that evidence defines the smallest useful Chromium patch set.

See `docs/ARCHITECTURE.md` and `docs/POWER_POLICY.md` for the design boundary and current limitations.

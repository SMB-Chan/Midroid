# Benchmarking Midroid against a Chrome/PWA baseline

Midroid's first milestone is not to claim a battery saving in advance. It is to make the WebView control layer measurable enough to decide whether deeper Chromium changes are justified.

## What to compare

Use the same phone and compare at least these targets:

1. Chrome/PWA baseline for the same Misskey instance.
2. Midroid Balanced.
3. Midroid Eco.

Performance mode is useful as a control because it removes most of Midroid's deliberate power restrictions.

Run each scenario at least three times. An A/B/A or randomized ordering is preferable to running all baseline samples first and all Midroid samples later, because battery temperature and cache warmth drift over time.

## Control the environment

Keep these variables as constant as practical:

- same device and Android/WebView versions;
- same Misskey account and timeline;
- same Wi-Fi or mobile network;
- same screen brightness;
- same system refresh-rate setting;
- similar battery temperature at the start of each run;
- same background-app state;
- same scenario duration and human interaction pattern.

Wireless ADB is preferable for real battery measurements because USB can charge the phone. If USB ADB is unavoidable, the capture script can tell Android to simulate an unplugged state for batterystats with `MIDROID_BATTERY_UNPLUG=1`; this improves Android's accounting but does **not** turn USB power measurements into a hardware power-meter measurement.

## Recommended scenarios

### Foreground idle

Open the home timeline, do not touch the phone, and keep the screen on for 10-15 minutes. This is useful for measuring WebSocket/timer/animation work while the page remains visible.

### Timeline interaction

For 5-10 minutes, scroll the same timeline at a repeatable pace and open approximately the same number of notes/media items. This stresses rendering, decode, JS, and network activity.

### Background residence

Open Misskey, send the app fully to the background so its activity reaches `onStop()`, leave it there for 20-30 minutes, then return. This is the most important test for Midroid's explicit `WebView.onPause()` + `pauseTimers()` deep-suspension policy and renderer reclaim behavior.

Do not count a merely `onPause()` state as background suspension: Android may pause an activity while it remains visible in multi-window or while transient UI is on top. `MidroidDiag` emits `visible` and `hidden` lifecycle events so the measurement can verify the transition actually happened.

### Media-heavy

Use a timeline containing animated emoji, images, GIF/video, or MFM effects for 5-10 minutes. Compare Balanced and Eco to quantify the reduced-motion/autoplay policy rather than assuming it helps.

## Capture command

For Midroid:

```text
bash tools/benchmark_android.sh dev.midroid.app 600 midroid-balanced-01
```

For Chrome itself:

```text
bash tools/benchmark_android.sh com.android.chrome 600 chrome-01
```

A PWA installed as a WebAPK may have its own package. On many devices you can discover candidates with:

```text
adb shell pm list packages | grep -i webapk
```

Use the actual package name in place of `com.android.chrome` when the installed Misskey PWA runs under a WebAPK package.

Optional environment variables:

```text
MIDROID_SAMPLE_INTERVAL=10
MIDROID_BATTERY_UNPLUG=1
ADB=/custom/path/to/adb
```

The script writes raw evidence rather than collapsing it into one score. This is intentional: Android vendors differ in what they expose, and renderer work can move between the app process and WebView sandbox processes.

## Files captured

Each run records:

- device/build/brightness/refresh metadata;
- WebView provider state;
- battery state before and after;
- package metadata and UID information;
- `dumpsys meminfo`;
- `dumpsys gfxinfo ... framestats`;
- `dumpsys cpuinfo` and periodic `top` snapshots;
- `dumpsys procstats`;
- package-scoped `batterystats`;
- raw `netstats` before and after;
- activity process state;
- jobscheduler state;
- Midroid's privacy-safe `MidroidDiag` lifecycle log when the target is Midroid.

## Primary metrics

Treat these as the first-pass metrics:

- foreground/background CPU time and process residency;
- package-attributed batterystats energy/charge where the device exposes it;
- wakelock/background activity evidence;
- memory footprint and renderer reclaim events;
- frame timing / jank during interactive scenarios;
- network deltas;
- battery temperature drift.

Do not infer real-world battery percentage savings from a single short run. A meaningful conclusion should come from repeated runs and, ideally, a long unplugged test or an external USB power meter.

## MidroidDiag

Midroid emits logcat events under the `MidroidDiag` tag. It records only the configured origin (scheme + host + non-default port), power mode, WebView provider/version, Android SDK level, power-saver state, lifecycle transitions, and renderer termination reason. URL paths, note IDs, query strings, and fragments are deliberately excluded.

Example collection:

```text
adb logcat -d -v threadtime MidroidDiag:I '*:S'
```

Use `visible` / `hidden` events to confirm a background test reached deep suspension, and renderer reclaim events to verify that Eco/Balanced policies are not creating unstable restore loops.

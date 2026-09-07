# Lifecycle policy

Midroid distinguishes **visible** from merely **resumed** activity state.

The WebView enters deep suspension from `Activity.onStop()`, not `onPause()`. This avoids freezing a still-visible Misskey surface in Android multi-window. A fully covering system activity such as some file pickers can still drive Midroid to `onStop()`, which is acceptable because the Misskey surface is then no longer visible.

## State transitions

| Android activity state | Midroid action |
| --- | --- |
| `onStart()` | Resume WebView timers and renderer activity, then re-apply the selected document and refresh-rate policy. |
| `onResume()` / `onPause()` | No deep WebView suspension. Android may pause an activity while it is still visible. |
| `onStop()` | Pause media, call `WebView.onPause()`, then `pauseTimers()`. |
| renderer reclaimed while hidden | Keep the last known safe URL and recreate the WebView when the activity becomes visible again. |

This policy favors correctness first: background work is aggressively suspended only once the activity is no longer visible, preserving split-screen behavior.

## Refresh-rate policy

Midroid treats refresh rate as a **scheduler hint**, not a guarantee.

- Android 16 / API 36+: Eco propagates a 60 Hz request through the WebView hierarchy with `ViewGroup.propagateRequestedFrameRate()`; the hint is applied again when the document is ready because WebView creates internal views lazily. Balanced and Performance restore the default category.
- Android 15 / API 35: Eco uses `View.setRequestedFrameRate(60f)` on the WebView; Balanced and Performance restore the default category.
- Android 8-14 / API 26-34: Midroid falls back to the window-level `preferredRefreshRate` hint.

The platform may choose a different physical display mode based on device capabilities and other visible surfaces. The benchmark protocol records display settings so the effect can be measured rather than assumed.

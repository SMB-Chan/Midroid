# Lifecycle policy

Midroid distinguishes **visible** from merely **resumed** activity state.

The WebView enters deep suspension from `Activity.onStop()`, not `onPause()`. This avoids freezing a still-visible Misskey surface in Android multi-window and avoids unnecessary suspend/resume churn around transient UI such as file pickers.

## State transitions

| Android activity state | Midroid action |
| --- | --- |
| `onStart()` | Resume WebView timers and renderer activity, then re-apply the selected document power policy. |
| `onResume()` / `onPause()` | No deep WebView suspension. Android may pause an activity while it is still visible. |
| `onStop()` | Pause media, call `WebView.onPause()`, then `pauseTimers()`. |
| renderer reclaimed while hidden | Keep the last known safe URL and recreate the WebView when the activity becomes visible again. |

This policy favors correctness first: background work is still aggressively suspended once the activity is no longer visible, while split-screen and transient system UI remain functional.

## Refresh-rate policy

On Android 11+ Midroid sends the Eco 60 Hz preference to the WebView itself using `View.setFrameRate()`. Older supported Android versions fall back to the window refresh-rate preference. Balanced and Performance clear the explicit request and let the system choose.

# Power policy

## Eco

- Requests a 60 Hz activity refresh rate.
- Uses `RENDERER_PRIORITY_BOUND` while visible and allows the renderer to become waived when not visible.
- Keeps the WebView media-gesture gate off so explicit Misskey lightbox playback is not rejected after the original tap task ends; Eco instead stops autoplay media and pauses media in the background.
- Injects a reversible reduced-motion stylesheet after page load.
- Stops autoplay media.

## Balanced

- Leaves the device refresh rate unconstrained.
- Uses important renderer priority and does not waive it when the activity is hidden (`setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, waivedWhenNotVisible = false)`).
- Preserves Misskey's normal foreground animations.

## Performance

- Leaves refresh rate unconstrained.
- Keeps the renderer at important priority even when the Activity is hidden.
- Preserves Misskey's normal foreground animations.

## All modes in background

- Pause HTML media immediately in `Activity.onStop()`.
- About 30 seconds later, if the activity is still stopped, call `WebView.onPause()` and then `pauseTimers()`.
- `Activity.onStart()` cancels that pending suspension and resumes timers and renderer activity.

A short app switch therefore does not freeze Misskey's JavaScript timers. Deep suspension still runs once the activity has stayed invisible for the grace period. This is intentionally stronger than relying only on generic browser heuristics. The policy can later be made adaptive using measured frame time, thermal state, network activity and battery discharge data.

## Settings

Opening settings hides the browser but keeps the WebView when the instance URL is not editable (the same account). While settings are showing, the renderer is temporarily held at important priority with waiver disabled so Android does not reclaim it. Saving the same account remounts that WebView and reapplies text zoom, reaction scale, and the selected power-mode policy without calling `loadUrl`. The WebView is rebuilt only when the account changes or is deleted. If the renderer dies while settings are open, Midroid does not cover the settings screen; it drops the WebView quietly and the next browser show creates a new one.

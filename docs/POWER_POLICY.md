# Power policy

## Eco

- Requests a 60 Hz activity refresh rate.
- Uses `RENDERER_PRIORITY_BOUND` while visible and allows the renderer to become waived when not visible.
- Requires a user gesture for media playback.
- Injects a reversible reduced-motion stylesheet after page load.
- Stops autoplay media.

## Balanced

- Leaves the device refresh rate unconstrained.
- Uses important renderer priority while visible but allows it to become waived when hidden.
- Preserves Misskey's normal foreground animations.

## Performance

- Leaves refresh rate unconstrained.
- Keeps the renderer at important priority even when the Activity is hidden.
- Preserves Misskey's normal foreground animations.

## All modes in background

- Pause HTML media where accessible.
- Call `WebView.onPause()`.
- Call `WebView.pauseTimers()`.

This is intentionally stronger than relying only on generic browser heuristics. The policy can later be made adaptive using measured frame time, thermal state, network activity and battery discharge data.
